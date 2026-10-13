import React, { useEffect, useMemo, useRef, useState } from 'react';
import './App.css';
import { logout } from './api/auth';
import {
  deleteGroupConversation,
  deleteMessage,
  editMessage,
  getConversationMessages,
  getConversations,
  getGroupMembers,
  sendMessage,
} from './api/chat';
import { getStoredUser, UNAUTHORIZED_EVENT } from './api/client';
import { wsManager } from './api/websocket';
import { AddMembersModal } from './components/AddMembersModal';
import { AuthModal } from './components/AuthModal';
import { NewConversationModal } from './components/NewConversationModal';
import type { AuthUser } from './types/auth';
import type {
  ConversationResponse,
  MemberResponse,
  MessageResponse,
  WsChatMessageEvent,
  WsConnectionStatus,
} from './types/chat';

const TONES = ['sage', 'coral', 'teal', 'blue', 'gold'] as const;

function getTone(id: number | string): string {
  if (typeof id === 'number') {
    return TONES[Math.abs(id) % TONES.length];
  }
  let hash = 0;
  for (let i = 0; i < id.length; i++) {
    hash = (hash << 5) - hash + id.charCodeAt(i);
  }
  return TONES[Math.abs(hash) % TONES.length];
}

function getInitials(name?: string | null): string {
  if (!name) return '??';
  const parts = name.trim().split(/\s+/);
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function formatTime(isoString?: string | null): string {
  if (!isoString) return '';
  const date = new Date(isoString);
  if (isNaN(date.getTime())) return '';
  const now = new Date();
  const isToday =
    date.getDate() === now.getDate() &&
    date.getMonth() === now.getMonth() &&
    date.getFullYear() === now.getFullYear();

  if (isToday) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  return date.toLocaleDateString([], { month: 'short', day: 'numeric' });
}

function orderConversationsByActivity(conversations: ConversationResponse[]): ConversationResponse[] {
  return [...conversations].sort((left, right) => {
    const timeDifference = new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime();
    return timeDifference || right.conversationId - left.conversationId;
  });
}

function applyMessageActivity(
  conversations: ConversationResponse[],
  conversationId: number,
  messageId: number,
  content: string,
  createdAt: string
): ConversationResponse[] {
  const index = conversations.findIndex((conversation) => conversation.conversationId === conversationId);
  if (index < 0) return conversations;

  const conversation = conversations[index];
  const currentTime = new Date(conversation.updatedAt).getTime();
  const incomingTime = new Date(createdAt).getTime();
  const isNewer = incomingTime > currentTime ||
    (incomingTime === currentTime && messageId > (conversation.lastMessageId ?? 0));
  if (!isNewer) return conversations;

  const updated = conversations.slice();
  updated[index] = {
    ...conversation,
    lastMessage: content,
    updatedAt: createdAt,
    lastMessageId: messageId,
  };
  return orderConversationsByActivity(updated);
}

function Avatar({
  initials,
  tone = 'sage',
  online = false,
}: {
  initials: string;
  tone?: string;
  online?: boolean;
}) {
  return (
    <span className={`avatar ${tone}`}>
      {initials}
      {online && <i className="online-dot" />}
    </span>
  );
}

export default function App() {
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(() => getStoredUser());
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [activeConversationId, setActiveConversationId] = useState<number | null>(null);
  const activeConversationIdRef = useRef<number | null>(null);
  const [messages, setMessages] = useState<MessageResponse[]>([]);

  const [loadingConversations, setLoadingConversations] = useState(false);
  const [conversationsError, setConversationsError] = useState<string | null>(null);

  const [loadingMessages, setLoadingMessages] = useState(false);
  const [messagesError, setMessagesError] = useState<string | null>(null);

  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);

  const [wsStatus, setWsStatus] = useState<WsConnectionStatus>('disconnected');

  const [query, setQuery] = useState('');
  const [draft, setDraft] = useState('');
  const [theme, setTheme] = useState<'light' | 'dark'>('light');
  const [detailsOpen, setDetailsOpen] = useState(true);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [isNewChatOpen, setIsNewChatOpen] = useState(false);

  // State chỉnh sửa tin nhắn
  const [editingMessageId, setEditingMessageId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState('');
  const [editLoading, setEditLoading] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);

  // State menu thao tác ba chấm trên mobile/desktop
  const [activeMenuMessageId, setActiveMenuMessageId] = useState<number | null>(null);

  // State xác nhận xóa tin nhắn
  const [deletingMessage, setDeletingMessage] = useState<MessageResponse | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  // State quản lý thành viên nhóm và xóa nhóm
  const [groupMembers, setGroupMembers] = useState<MemberResponse[]>([]);
  const [isAddMemberOpen, setIsAddMemberOpen] = useState(false);
  const [isDeleteGroupOpen, setIsDeleteGroupOpen] = useState(false);
  const [deleteGroupLoading, setDeleteGroupLoading] = useState(false);
  const [deleteGroupError, setDeleteGroupError] = useState<string | null>(null);

  const messagesSectionRef = useRef<HTMLElement>(null);
  const isNearBottomRef = useRef(true);
  const conversationRefreshVersionRef = useRef(0);

  function selectConversation(conversationId: number | null) {
    activeConversationIdRef.current = conversationId;
    setActiveConversationId(conversationId);
  }

  // Đóng dropdown menu khi click ra ngoài
  useEffect(() => {
    function handleGlobalClick() {
      setActiveMenuMessageId(null);
    }
    window.addEventListener('click', handleGlobalClick);
    return () => {
      window.removeEventListener('click', handleGlobalClick);
    };
  }, []);

  // Cuộn vùng tin nhắn an toàn, không làm cuộn window hay các phần tử cha
  function scrollChatToBottom(smooth = false) {
    const container = messagesSectionRef.current;
    if (!container) return;
    if (smooth) {
      container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' });
    } else {
      container.scrollTop = container.scrollHeight;
    }
  }

  // Theo dõi vị trí cuộn của người dùng trong vùng lịch sử tin nhắn
  function handleMessagesScroll() {
    const el = messagesSectionRef.current;
    if (!el) return;
    const threshold = 120; // 120px từ đáy
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    isNearBottomRef.current = distanceFromBottom <= threshold;
  }

  // Lắng nghe sự kiện 401 Unauthorized
  useEffect(() => {
    function handleUnauthorized() {
      wsManager.disconnect();
      setCurrentUser(null);
      setConversations([]);
      selectConversation(null);
      setMessages([]);
    }
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => {
      window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    };
  }, []);

  // Quản lý kết nối WebSocket theo phiên đăng nhập
  useEffect(() => {
    if (!currentUser?.token) {
      wsManager.disconnect();
      return;
    }

    const unsubscribeStatus = wsManager.onStatusChange(setWsStatus);
    wsManager.connect(currentUser.token);

    return () => {
      unsubscribeStatus();
      wsManager.disconnect();
    };
  }, [currentUser]);

  // Tải danh sách hội thoại khi đã có tài khoản
  useEffect(() => {
    if (!currentUser) return;

    let mounted = true;
    async function fetchConversations() {
      const requestVersion = ++conversationRefreshVersionRef.current;
      setLoadingConversations(true);
      setConversationsError(null);
      try {
        const data = await getConversations();
        if (!mounted || requestVersion !== conversationRefreshVersionRef.current) return;
        setConversations(orderConversationsByActivity(data));
        if (data.length > 0) {
          setActiveConversationId((prev) => {
            if (prev && data.some((c) => c.conversationId === prev)) {
              activeConversationIdRef.current = prev;
              return prev;
            }
            const nextId = data[0].conversationId;
            activeConversationIdRef.current = nextId;
            return nextId;
          });
        } else {
          selectConversation(null);
        }
      } catch (err: any) {
        if (!mounted) return;
        setConversationsError(err.message || 'Không thể tải danh sách cuộc trò chuyện');
      } finally {
        if (mounted) setLoadingConversations(false);
      }
    }

    fetchConversations();

    return () => {
      mounted = false;
    };
  }, [currentUser]);

  useEffect(() => {
    if (!currentUser) return;

    return wsManager.subscribeToAccountEvents((event) => {
      if (
        event.type !== 'CONVERSATION_CREATED' &&
        event.type !== 'CONVERSATION_MEMBERS_ADDED' &&
        event.type !== 'MESSAGE_CREATED'
      ) {
        return;
      }

      const requestVersion = ++conversationRefreshVersionRef.current;
      getConversations()
        .then((data) => {
          if (requestVersion !== conversationRefreshVersionRef.current) return;
          setConversations(orderConversationsByActivity(data));
        })
        .catch((error: unknown) => {
          console.error('Không thể đồng bộ danh sách cuộc trò chuyện:', error);
        });
    });
  }, [currentUser]);

  // Tải tin nhắn khi đổi cuộc trò chuyện (REST history)
  useEffect(() => {
    if (!currentUser || activeConversationId === null) {
      return;
    }

    let mounted = true;
    async function fetchMessages(convId: number) {
      setLoadingMessages(true);
      setMessagesError(null);
      try {
        const page = await getConversationMessages(convId, 0, 100);
        if (!mounted) return;
        // Backend trả về DESC (mới nhất trước) -> Đảo ngược để hiển thị dòng thời gian cũ -> mới
        setMessages(page.content.slice().reverse());

        // Sau khi lịch sử tải xong, cuộn tức thì xuống tin nhắn mới nhất
        requestAnimationFrame(() => {
          scrollChatToBottom(false);
          isNearBottomRef.current = true;
        });
      } catch (err: any) {
        if (!mounted) return;
        setMessagesError(err.message || 'Không thể tải lịch sử tin nhắn');
      } finally {
        if (mounted) setLoadingMessages(false);
      }
    }

    fetchMessages(activeConversationId);

    return () => {
      mounted = false;
    };
  }, [currentUser, activeConversationId]);

  // Tải danh sách thành viên nhóm khi mở cuộc trò chuyện GROUP
  useEffect(() => {
    if (!currentUser || activeConversationId === null) {
      setGroupMembers([]);
      return;
    }

    const conv = conversations.find((c) => c.conversationId === activeConversationId);
    if (!conv || conv.conversationType !== 'GROUP') {
      setGroupMembers([]);
      return;
    }

    let mounted = true;
    getGroupMembers(activeConversationId)
      .then((members) => {
        if (mounted) {
          setGroupMembers(members);
        }
      })
      .catch((err) => {
        console.error('Không thể tải danh sách thành viên nhóm', err);
      });

    return () => {
      mounted = false;
    };
  }, [currentUser, activeConversationId, conversations]);

  // Subscribe WebSocket topic của cuộc trò chuyện đang mở
  useEffect(() => {
    if (!currentUser || activeConversationId === null) {
      return;
    }

    const unsubscribe = wsManager.subscribeToConversation(
      activeConversationId,
      (event: WsChatMessageEvent) => {
        if (event.type === 'MESSAGE_CREATED') {
          if (activeConversationIdRef.current === event.conversationId && event.id !== undefined) {
            const newMsg: MessageResponse = {
              id: event.id,
              conversationId: event.conversationId,
              senderUsername: event.sender || '',
              content: event.content || '',
              createdAt: event.createdAt || new Date().toISOString(),
              editedAt: event.editedAt || null,
            };

            setMessages((prev) => {
              if (activeConversationIdRef.current !== event.conversationId) return prev;
              if (prev.some((m) => m.id === newMsg.id)) return prev;
              return [...prev, newMsg];
            });

            const isMine = event.sender === currentUser.username;
            if (isMine) {
              // Người dùng tự gửi -> cuộn xuống đáy
              requestAnimationFrame(() => {
                scrollChatToBottom(true);
                isNearBottomRef.current = true;
              });
            } else if (isNearBottomRef.current) {
              // Người khác gửi VÀ người dùng đang ở cuối danh sách -> tự cuộn xuống
              requestAnimationFrame(() => {
                scrollChatToBottom(true);
              });
            }
            // Nếu người dùng đang cuộn lên đọc tin cũ -> giữ nguyên vị trí cuộn, không ép cuộn!
          }

          // Cập nhật tin nhắn cuối cùng và thời gian ở sidebar
          if (event.id !== undefined && event.createdAt) {
            setConversations((previous) => applyMessageActivity(
              previous,
              event.conversationId,
              event.id!,
              event.content || '',
              event.createdAt!
            ));
          }
        } else if (event.type === 'MESSAGE_UPDATED') {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === event.id
                ? {
                    ...m,
                    content: event.content || m.content,
                    editedAt: event.editedAt || m.editedAt,
                  }
                : m
            )
          );
        } else if (event.type === 'MESSAGE_DELETED') {
          setMessages((prev) => prev.filter((m) => m.id !== event.id));
        } else if (event.type === 'CONVERSATION_MEMBERS_ADDED') {
          if (event.conversationId === activeConversationId) {
            getGroupMembers(activeConversationId)
              .then((updatedMembers) => {
                setGroupMembers(updatedMembers);
                setConversations((prev) =>
                  prev.map((c) =>
                    c.conversationId === activeConversationId
                      ? { ...c, memberCount: updatedMembers.length }
                      : c
                  )
                );
              })
              .catch((err) => {
                console.error('Không thể cập nhật danh sách thành viên qua WebSocket', err);
              });
          }
        } else if (event.type === 'CONVERSATION_DELETED') {
          if (event.conversationId === activeConversationId) {
            setConversations((prev) =>
              prev.filter((c) => c.conversationId !== event.conversationId)
            );
            selectConversation(null);
            setGroupMembers([]);
            setIsDeleteGroupOpen(false);
            setIsAddMemberOpen(false);
          }
        }
      }
    );

    return () => {
      unsubscribe();
    };
  }, [currentUser, activeConversationId]);

  const activeConversation = useMemo(
    () => conversations.find((c) => c.conversationId === activeConversationId) ?? null,
    [conversations, activeConversationId]
  );

  const activeChatDisplayName = useMemo(() => {
    if (!activeConversation) return '';
    if (activeConversation.conversationType === 'DIRECT') {
      return activeConversation.participantUsername || 'Người dùng';
    }
    return activeConversation.name || 'Nhóm chat';
  }, [activeConversation]);

  const isGroupOwner = useMemo(() => {
    if (!activeConversation || activeConversation.conversationType !== 'GROUP' || !currentUser) {
      return false;
    }
    return activeConversation.ownerUsername === currentUser.username;
  }, [activeConversation, currentUser]);

  const filteredConversations = useMemo(() => {
    if (!query.trim()) return conversations;
    const q = query.toLowerCase();
    return conversations.filter((c) => {
      const name =
        c.conversationType === 'DIRECT'
          ? (c.participantUsername || '').toLowerCase()
          : (c.name || '').toLowerCase();
      const last = (c.lastMessage || '').toLowerCase();
      return name.includes(q) || last.includes(q);
    });
  }, [conversations, query]);

  const userInitials = useMemo(() => {
    if (!currentUser?.username) return 'ZU';
    return getInitials(currentUser.username);
  }, [currentUser]);

  function handleLogout() {
    wsManager.disconnect();
    logout();
    setCurrentUser(null);
    setConversations([]);
    selectConversation(null);
    setMessages([]);
  }

  function handleConversationCreated(newConv: ConversationResponse) {
    setIsNewChatOpen(false);
    setConversations((prev) => {
      const exists = prev.some((c) => c.conversationId === newConv.conversationId);
      if (exists) {
        return prev.map((c) => (c.conversationId === newConv.conversationId ? newConv : c));
      }
      return [newConv, ...prev];
    });
    selectConversation(newConv.conversationId);
    setSidebarOpen(false);
  }

  function handleStartEdit(msg: MessageResponse) {
    setEditingMessageId(msg.id);
    setEditDraft(msg.content);
    setEditError(null);
    setActiveMenuMessageId(null);
  }

  function handleCancelEdit() {
    setEditingMessageId(null);
    setEditDraft('');
    setEditError(null);
  }

  async function handleSaveEdit(messageId: number) {
    if (editLoading) return;
    const text = editDraft.trim();
    if (!text) {
      setEditError('Nội dung tin nhắn không được để trống');
      return;
    }

    setEditLoading(true);
    setEditError(null);

    try {
      const updated = await editMessage(messageId, text);
      setMessages((prev) =>
        prev.map((m) =>
          m.id === messageId
            ? { ...m, content: updated.content, editedAt: updated.editedAt }
            : m
        )
      );

      // Cập nhật preview ở sidebar nếu đây là tin nhắn cuối
      setConversations((prev) =>
        prev.map((c) =>
          c.conversationId === activeConversationId
            ? { ...c, lastMessage: updated.content }
            : c
        )
      );

      setEditingMessageId(null);
      setEditDraft('');
    } catch (err: any) {
      setEditError(err?.message || 'Không thể chỉnh sửa tin nhắn');
    } finally {
      setEditLoading(false);
    }
  }

  function handleConfirmDelete(msg: MessageResponse) {
    setDeletingMessage(msg);
    setDeleteError(null);
    setActiveMenuMessageId(null);
  }

  async function handleExecuteDelete() {
    if (!deletingMessage || deleteLoading) return;

    setDeleteLoading(true);
    setDeleteError(null);

    try {
      await deleteMessage(deletingMessage.id);
      const deletedId = deletingMessage.id;
      setMessages((prev) => prev.filter((m) => m.id !== deletedId));
      setDeletingMessage(null);
    } catch (err: any) {
      setDeleteError(err?.message || 'Không thể xóa tin nhắn');
    } finally {
      setDeleteLoading(false);
    }
  }

  function handleMembersAdded(updatedMembers: MemberResponse[]) {
    setGroupMembers(updatedMembers);
    if (activeConversationId) {
      setConversations((prev) =>
        prev.map((c) =>
          c.conversationId === activeConversationId
            ? { ...c, memberCount: updatedMembers.length }
            : c
        )
      );
    }
  }

  async function handleExecuteDeleteGroup() {
    if (!activeConversationId || deleteGroupLoading) return;

    setDeleteGroupLoading(true);
    setDeleteGroupError(null);

    try {
      await deleteGroupConversation(activeConversationId);
      const deletedId = activeConversationId;
      setConversations((prev) => prev.filter((c) => c.conversationId !== deletedId));
      selectConversation(null);
      setGroupMembers([]);
      setIsDeleteGroupOpen(false);
    } catch (err: any) {
      setDeleteGroupError(err?.message || 'Không thể xóa nhóm trò chuyện');
    } finally {
      setDeleteGroupLoading(false);
    }
  }

  async function handleSendMessage(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!activeConversationId || sending) return;
    const targetConversationId = activeConversationId;
    const text = draft.trim();
    if (!text) return;

    setSending(true);
    setSendError(null);

    // 1. Thử gửi qua WebSocket STOMP (/app/chat.send)
    const wsSent = wsManager.sendMessage(targetConversationId, text);
    if (wsSent) {
      setDraft('');
      setSending(false);
      requestAnimationFrame(() => {
        scrollChatToBottom(true);
        isNearBottomRef.current = true;
      });
      return;
    }

    // 2. Fallback REST API nếu WebSocket chưa kết nối
    try {
      const savedMessage = await sendMessage(targetConversationId, text);

      setMessages((prev) => {
        if (activeConversationIdRef.current !== targetConversationId) return prev;
        if (prev.some((m) => m.id === savedMessage.id)) return prev;
        return [...prev, savedMessage];
      });

      setConversations((previous) => applyMessageActivity(
        previous,
        targetConversationId,
        savedMessage.id,
        savedMessage.content,
        savedMessage.createdAt
      ));

      if (activeConversationIdRef.current === targetConversationId) {
        setDraft('');
        requestAnimationFrame(() => {
          if (activeConversationIdRef.current !== targetConversationId) return;
          scrollChatToBottom(true);
          isNearBottomRef.current = true;
        });
      }
    } catch (err: any) {
      setSendError(err?.message || 'Không thể gửi tin nhắn. Vui lòng thử lại.');
    } finally {
      setSending(false);
    }
  }

  return (
    <div className={`app theme-${theme}`}>
      <div className="shell">
        {/* Navigation Rail */}
        <nav className="rail" aria-label="Main navigation">
          <div className="brand-mark">z.</div>
          <button className="rail-btn active" title="Chats" aria-label="Chats">▤</button>
          <button className="rail-btn" title="Friends" aria-label="Friends">♧</button>
          <button className="rail-btn" title="Explore" aria-label="Explore">⌘</button>
          <button className="rail-btn" title="Saved" aria-label="Saved">♡</button>
          <div className="rail-spacer" />
          <button
            className="rail-btn"
            onClick={() => setTheme(theme === 'light' ? 'dark' : 'light')}
            title="Đổi giao diện sáng/tối"
            aria-label="Đổi giao diện sáng/tối"
          >
            ◐
          </button>
          <Avatar initials={userInitials} tone="coral" />
        </nav>

        {/* Sidebar Danh Sách Cuộc Trò Chuyện */}
        <aside className={`chats ${sidebarOpen ? 'mobile-open' : ''}`}>
          <div className="brand-row">
            <div>
              <div className="wordmark">
                CHAT<span>ZUN</span>
              </div>
              <div className="eyebrow">Your little corner</div>
            </div>
            <button
              className="round-btn"
              title="Cuộc trò chuyện mới"
              aria-label="Cuộc trò chuyện mới"
              onClick={() => setIsNewChatOpen(true)}
            >
              ＋
            </button>
          </div>

          <label className="search">
            <span>⌕</span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Tìm kiếm cuộc trò chuyện..."
            />
          </label>

          <div className="section-row">
            <span className="eyebrow">Hội thoại</span>
            <button
              className="text-plus"
              aria-label="Thêm cuộc trò chuyện"
              onClick={() => setIsNewChatOpen(true)}
            >
              ＋
            </button>
          </div>

          <div className="chat-list">
            {loadingConversations && (
              <div className="state-notice">Đang tải danh sách cuộc trò chuyện...</div>
            )}

            {conversationsError && (
              <div className="state-notice error">
                <span>⚠ {conversationsError}</span>
                <button
                  className="link-btn"
                  onClick={() => {
                    setLoadingConversations(true);
                    getConversations()
                      .then(setConversations)
                      .finally(() => setLoadingConversations(false));
                  }}
                >
                  Thử lại
                </button>
              </div>
            )}

            {!loadingConversations && !conversationsError && filteredConversations.length === 0 && (
              <div className="state-notice">
                {query.trim()
                  ? 'Không tìm thấy cuộc trò chuyện phù hợp.'
                  : 'Chưa có cuộc trò chuyện nào. Bấm ＋ để nhắn tin!'}
              </div>
            )}

            {filteredConversations.map((conv) => {
              const displayName =
                conv.conversationType === 'DIRECT'
                  ? conv.participantUsername || 'Người dùng'
                  : conv.name || 'Nhóm chat';
              const initials = getInitials(displayName);
              const tone = getTone(conv.conversationId);
              const preview = conv.lastMessage || 'Chưa có tin nhắn';
              const time = formatTime(conv.updatedAt || conv.createdAt);
              const isSelected = conv.conversationId === activeConversationId;

              return (
                <button
                  key={conv.conversationId}
                  className={`chat-item ${isSelected ? 'selected' : ''}`}
                  onClick={() => {
                    selectConversation(conv.conversationId);
                    setSidebarOpen(false);
                  }}
                >
                  <Avatar initials={initials} tone={tone} />
                  <span className="chat-info">
                    <span className="chat-name-row">
                      <strong>{displayName}</strong>
                      <small>{time}</small>
                    </span>
                    <span className="preview">{preview}</span>
                  </span>
                </button>
              );
            })}
          </div>

          <div className="sidebar-bottom">
            <Avatar initials={userInitials} tone="coral" />
            <div className="profile-copy">
              <strong>{currentUser ? currentUser.username : 'Chưa đăng nhập'}</strong>
              <span className="ws-badge">
                <i className={`ws-dot ${wsStatus}`} />
                {currentUser
                  ? wsStatus === 'connected'
                    ? 'Trực tuyến'
                    : wsStatus === 'connecting'
                    ? 'Đang kết nối...'
                    : 'Ngoại tuyến'
                  : 'Khách'}
              </span>
            </div>
            {currentUser && (
              <button
                className="round-btn logout-btn"
                onClick={handleLogout}
                title="Đăng xuất"
                aria-label="Đăng xuất"
              >
                ⎋
              </button>
            )}
          </div>
        </aside>

        {/* Khung Chat Chính */}
        <main className="main">
          {activeConversation ? (
            <>
              <header className="chat-header">
                <div className="person">
                  <button
                    className="round-btn mobile-menu"
                    onClick={() => setSidebarOpen(!sidebarOpen)}
                    aria-label="Mở danh sách trò chuyện"
                  >
                    ☰
                  </button>
                  <Avatar
                    initials={getInitials(activeChatDisplayName)}
                    tone={getTone(activeConversation.conversationId)}
                  />
                  <div>
                    <h1>{activeChatDisplayName}</h1>
                    <p className="status">
                      <span className={`ws-dot ${wsStatus}`} />
                      {activeConversation.conversationType === 'DIRECT' ? 'Trò chuyện trực tiếp' : 'Nhóm chat'}
                      <span style={{ opacity: 0.75, fontSize: 10, marginLeft: 6 }}>
                        ({wsStatus === 'connected' ? 'Realtime' : wsStatus === 'connecting' ? 'Đang kết nối...' : 'Ngoại tuyến'})
                      </span>
                    </p>
                  </div>
                </div>
                <div className="header-actions">
                  <button className="round-btn optional" title="Tìm trong chat" aria-label="Tìm trong chat">⌕</button>
                  <button
                    className="round-btn"
                    onClick={() => setDetailsOpen(!detailsOpen)}
                    title="Thông tin cuộc trò chuyện"
                    aria-label="Thông tin cuộc trò chuyện"
                  >
                    ⓘ
                  </button>
                  <div className="theme-toggle">
                    <button
                      className={theme === 'light' ? 'active' : ''}
                      onClick={() => setTheme('light')}
                      aria-label="Chế độ sáng"
                    >
                      ☼
                    </button>
                    <button
                      className={theme === 'dark' ? 'active' : ''}
                      onClick={() => setTheme('dark')}
                      aria-label="Chế độ tối"
                    >
                      ☾
                    </button>
                  </div>
                </div>
              </header>

              {sendError && (
                <div className="chat-error-banner" role="alert">
                  <span>{sendError}</span>
                  <button onClick={() => setSendError(null)}>×</button>
                </div>
              )}

              <section
                className="messages"
                aria-live="polite"
                ref={messagesSectionRef}
                onScroll={handleMessagesScroll}
              >
                <div className="day-separator">LỊCH SỬ TIN NHẮN</div>

                {loadingMessages && (
                  <div className="state-notice">Đang tải tin nhắn...</div>
                )}

                {messagesError && (
                  <div className="state-notice error">
                    <span>⚠ {messagesError}</span>
                    <button
                      className="link-btn"
                      onClick={() => {
                        if (activeConversationId) {
                          setLoadingMessages(true);
                          getConversationMessages(activeConversationId, 0, 100)
                            .then((p) => {
                              setMessages(p.content.slice().reverse());
                              requestAnimationFrame(() => {
                                scrollChatToBottom(false);
                                isNearBottomRef.current = true;
                              });
                            })
                            .finally(() => setLoadingMessages(false));
                        }
                      }}
                    >
                      Thử tải lại
                    </button>
                  </div>
                )}

                {!loadingMessages && !messagesError && messages.length === 0 && (
                  <div className="state-notice">
                    Chưa có tin nhắn nào trong cuộc trò chuyện này. Hãy gửi tin nhắn đầu tiên! ✨
                  </div>
                )}

                {messages.map((message) => {
                  const isMine =
                    Boolean(currentUser) &&
                    message.senderUsername === currentUser?.username;
                  const authorInitials = getInitials(message.senderUsername);
                  const authorTone = getTone(message.senderUsername);
                  const time = formatTime(message.createdAt);
                  const isEditingThis = editingMessageId === message.id;

                  return (
                    <article
                      key={message.id}
                      className={`message ${isMine ? 'mine' : ''} ${activeMenuMessageId === message.id ? 'menu-active' : ''}`}
                      onClick={() => {
                        // Trên mobile/màn hình cảm ứng: chạm vào tin nhắn để mở menu nếu chưa mở
                        if (isMine && !isEditingThis) {
                          setActiveMenuMessageId((prev) => (prev === message.id ? null : message.id));
                        }
                      }}
                    >
                      {!isMine && <Avatar initials={authorInitials} tone={authorTone} />}

                      {/* Menu thao tác cho tin nhắn của người dùng hiện tại (chỉ hiển thị bên trái tin nhắn gửi đi) */}
                      {isMine && !isEditingThis && (
                        <div
                          className="msg-menu-wrap"
                          onClick={(e) => e.stopPropagation()}
                        >
                          <button
                            type="button"
                            className="msg-menu-btn"
                            title="Tùy chọn tin nhắn"
                            aria-label="Tùy chọn tin nhắn"
                            onClick={() =>
                              setActiveMenuMessageId((prev) =>
                                prev === message.id ? null : message.id
                              )
                            }
                          >
                            ⋯
                          </button>

                          {activeMenuMessageId === message.id && (
                            <div className="msg-dropdown" role="menu">
                              <button
                                type="button"
                                className="msg-dropdown-item"
                                onClick={() => handleStartEdit(message)}
                              >
                                <span className="action-icon">✎</span> Chỉnh sửa
                              </button>
                              <button
                                type="button"
                                className="msg-dropdown-item danger"
                                onClick={() => handleConfirmDelete(message)}
                              >
                                <span className="action-icon">🗑</span> Thu hồi / Xóa
                              </button>
                            </div>
                          )}
                        </div>
                      )}

                      <div className="message-body">
                        <div className="message-meta">
                          <strong>{isMine ? 'Bạn' : message.senderUsername}</strong>
                          <span>{time}</span>
                          {message.editedAt && (
                            <span
                              className="edited-badge"
                              title={`Đã chỉnh sửa lúc ${formatTime(message.editedAt)}`}
                            >
                              (đã chỉnh sửa)
                            </span>
                          )}
                        </div>

                        {isEditingThis ? (
                          <div
                            className="inline-edit-box"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <textarea
                              className="inline-edit-input"
                              value={editDraft}
                              onChange={(e) => setEditDraft(e.target.value)}
                              onKeyDown={(e) => {
                                if (e.key === 'Enter' && !e.shiftKey) {
                                  e.preventDefault();
                                  handleSaveEdit(message.id);
                                } else if (e.key === 'Escape') {
                                  e.preventDefault();
                                  handleCancelEdit();
                                }
                              }}
                              disabled={editLoading}
                              autoFocus
                              placeholder="Nhập nội dung mới... (Enter để lưu, Escape để hủy)"
                            />
                            {editError && (
                              <div className="inline-edit-error" role="alert">
                                ⚠ {editError}
                              </div>
                            )}
                            <div className="inline-edit-actions">
                              <button
                                type="button"
                                className="inline-btn cancel"
                                onClick={handleCancelEdit}
                                disabled={editLoading}
                              >
                                Hủy (Esc)
                              </button>
                              <button
                                type="button"
                                className="inline-btn save"
                                onClick={() => handleSaveEdit(message.id)}
                                disabled={editLoading || !editDraft.trim()}
                              >
                                {editLoading ? 'Đang lưu...' : 'Lưu (Enter)'}
                              </button>
                            </div>
                          </div>
                        ) : (
                          <div className="bubble">
                            {message.content.split('\n').map((line, idx) => (
                              <p key={idx}>{line}</p>
                            ))}
                          </div>
                        )}
                      </div>

                      {isMine && <Avatar initials={userInitials} tone="blue" />}
                    </article>
                  );
                })}
              </section>

              <div className="composer-wrap">
                <form className="composer" onSubmit={handleSendMessage}>
                  <button
                    type="button"
                    className="composer-tool"
                    title="Đính kèm"
                    aria-label="Đính kèm"
                  >
                    ＋
                  </button>
                  <input
                    value={draft}
                    onChange={(event) => setDraft(event.target.value)}
                    placeholder={`Nhắn tin cho ${activeChatDisplayName}...`}
                    aria-label="Nhập tin nhắn"
                    disabled={sending}
                  />
                  <button
                    className="send-btn"
                    type="submit"
                    aria-label="Gửi tin nhắn"
                    disabled={sending || !draft.trim()}
                    style={{ opacity: sending || !draft.trim() ? 0.6 : 1 }}
                  >
                    {sending ? '…' : '↑'}
                  </button>
                </form>
                <p className="composer-hint">
                  Nhấn <b>Enter</b> để gửi · Be kind, always ✦
                </p>
              </div>
            </>
          ) : (
            <div className="empty-chat-view">
              <div className="brand-mark">z.</div>
              <h3>Chào mừng đến với CHATZUN</h3>
              <p>
                Chọn một cuộc trò chuyện từ danh sách bên trái hoặc tạo cuộc trò chuyện mới để bắt đầu nhắn tin.
              </p>
              <button
                className="action-pill-btn"
                onClick={() => setIsNewChatOpen(true)}
              >
                ＋ Bắt đầu cuộc trò chuyện mới
              </button>
            </div>
          )}
        </main>

        {/* Panel Chi Tiết Bên Phải */}
        {detailsOpen && activeConversation && (
          <aside className="details">
            <div className="details-head">
              <span className="eyebrow">Chi tiết không gian</span>
              <button
                className="round-btn"
                onClick={() => setDetailsOpen(false)}
                aria-label="Đóng chi tiết"
              >
                ×
              </button>
            </div>
            <div className="cover">
              <span>z</span>
            </div>
            <div className="group-avatar">
              {getInitials(activeChatDisplayName)}
            </div>
            <h2 className="group-title">{activeChatDisplayName}</h2>
            <p className="group-sub">
              {activeConversation.conversationType === 'DIRECT'
                ? 'Cuộc trò chuyện trực tiếp · CHATZUN'
                : 'Nhóm không gian · CHATZUN'}
            </p>
            <p className="group-desc">
              Không gian trò chuyện riêng tư và an toàn trên nền tảng CHATZUN. ✨
            </p>
            <div className="stats">
              <div className="stat">
                <strong>
                  {activeConversation.conversationType === 'DIRECT'
                    ? '2'
                    : activeConversation.memberCount ?? (groupMembers.length > 0 ? groupMembers.length : '—')}
                </strong>
                <span>Thành viên</span>
              </div>
              <div className="stat">
                <strong>{formatTime(activeConversation.createdAt)}</strong>
                <span>Ngày tạo</span>
              </div>
            </div>
            <div className="detail-section">
              <div className="section-row">
                <span className="eyebrow">Thành viên tham gia</span>
                {activeConversation.conversationType === 'GROUP' && (
                  <button
                    className="text-plus"
                    title="Thêm thành viên vào nhóm"
                    aria-label="Thêm thành viên vào nhóm"
                    onClick={() => setIsAddMemberOpen(true)}
                  >
                    ＋
                  </button>
                )}
              </div>

              {activeConversation.conversationType === 'DIRECT' ? (
                <>
                  <div className="member">
                    <Avatar initials={userInitials} tone="blue" />
                    <strong>{currentUser?.username}</strong>
                    <small>Bạn</small>
                  </div>
                  {activeConversation.participantUsername && (
                    <div className="member">
                      <Avatar
                        initials={getInitials(activeConversation.participantUsername)}
                        tone={getTone(activeConversation.participantUsername)}
                      />
                      <strong>{activeConversation.participantUsername}</strong>
                      <small>Thành viên</small>
                    </div>
                  )}
                </>
              ) : (
                <div className="member-list-scroll">
                  {groupMembers.map((member) => {
                    const isMe = member.username === currentUser?.username;
                    const isOwner = member.username === activeConversation.ownerUsername;
                    let roleLabel = 'Thành viên';
                    if (isOwner) {
                      roleLabel = 'Chủ nhóm';
                    } else if (member.role === 'ADMIN') {
                      roleLabel = 'Quản trị viên';
                    }
                    if (isMe) {
                      roleLabel += ' (Bạn)';
                    }

                    return (
                      <div key={member.userId} className="member">
                        <Avatar
                          initials={getInitials(member.username)}
                          tone={getTone(member.userId)}
                        />
                        <strong>{member.username}</strong>
                        <small>{roleLabel}</small>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>

            {isGroupOwner && (
              <div className="detail-section danger-zone">
                <button
                  type="button"
                  className="danger-action-btn"
                  onClick={() => {
                    setDeleteGroupError(null);
                    setIsDeleteGroupOpen(true);
                  }}
                >
                  🗑 Xóa nhóm trò chuyện
                </button>
              </div>
            )}
          </aside>
        )}
      </div>

      {/* Modal Đăng nhập / Đăng ký khi chưa xác thực */}
      {!currentUser && (
        <AuthModal
          onSuccess={(user) => {
            setCurrentUser(user);
          }}
        />
      )}

      {/* Modal Mở Cuộc Trò Chuyện Mới */}
      {isNewChatOpen && currentUser && (
        <NewConversationModal
          onClose={() => setIsNewChatOpen(false)}
          onCreated={handleConversationCreated}
          currentUsername={currentUser.username}
        />
      )}

      {/* Modal Xác Nhận Xóa / Thu Hồi Tin Nhắn */}
      {deletingMessage && (
        <div
          className="auth-overlay"
          onClick={() => {
            if (!deleteLoading) setDeletingMessage(null);
          }}
        >
          <div
            className="auth-card modal-confirm"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-confirm-title"
          >
            <div className="details-head" style={{ marginBottom: 12 }}>
              <span className="eyebrow">Xác nhận</span>
              <button
                className="round-btn"
                type="button"
                onClick={() => setDeletingMessage(null)}
                disabled={deleteLoading}
                aria-label="Đóng"
              >
                ×
              </button>
            </div>

            <h3 id="delete-confirm-title" style={{ margin: '0 0 8px', fontSize: 18 }}>
              Thu hồi / Xóa tin nhắn?
            </h3>
            <p className="auth-desc" style={{ margin: '0 0 16px' }}>
              Tin nhắn này sẽ được xóa khỏi cuộc trò chuyện đối với tất cả thành viên.
            </p>

            <div className="delete-preview-box">
              <p>"{deletingMessage.content}"</p>
            </div>

            {deleteError && (
              <div className="auth-alert error" role="alert" style={{ marginTop: 14 }}>
                <span>⚠</span> {deleteError}
              </div>
            )}

            <div className="confirm-modal-actions">
              <button
                type="button"
                className="link-btn cancel-confirm-btn"
                onClick={() => setDeletingMessage(null)}
                disabled={deleteLoading}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                className="danger-confirm-btn"
                onClick={handleExecuteDelete}
                disabled={deleteLoading}
              >
                {deleteLoading ? 'Đang xóa...' : 'Xóa tin nhắn'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Modal Thêm Thành Viên Vào Nhóm */}
      {isAddMemberOpen && activeConversation && activeConversation.conversationType === 'GROUP' && currentUser && (
        <AddMembersModal
          conversationId={activeConversation.conversationId}
          groupName={activeChatDisplayName}
          existingMemberUsernames={groupMembers.map((m) => m.username)}
          currentUsername={currentUser.username}
          onClose={() => setIsAddMemberOpen(false)}
          onMembersAdded={handleMembersAdded}
        />
      )}

      {/* Modal Xác Nhận Xóa Nhóm Trò Chuyện (Chỉ dành cho chủ nhóm) */}
      {isDeleteGroupOpen && activeConversation && (
        <div
          className="auth-overlay"
          onClick={() => {
            if (!deleteGroupLoading) setIsDeleteGroupOpen(false);
          }}
        >
          <div
            className="auth-card modal-confirm"
            onClick={(e) => e.stopPropagation()}
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-group-confirm-title"
          >
            <div className="details-head" style={{ marginBottom: 12 }}>
              <span className="eyebrow">Xác nhận</span>
              <button
                className="round-btn"
                type="button"
                onClick={() => setIsDeleteGroupOpen(false)}
                disabled={deleteGroupLoading}
                aria-label="Đóng"
              >
                ×
              </button>
            </div>

            <h3 id="delete-group-confirm-title" style={{ margin: '0 0 8px', fontSize: 18 }}>
              Xóa nhóm trò chuyện?
            </h3>
            <p className="auth-desc" style={{ margin: '0 0 16px' }}>
              Bạn có chắc chắn muốn xóa nhóm <strong>"{activeChatDisplayName}"</strong>? Sau khi xóa, tất cả thành viên sẽ không thể gửi tin nhắn hay truy cập nhóm này nữa.
            </p>

            <div className="delete-preview-box">
              <p>Cảnh báo: Thao tác này là vĩnh viễn và không thể hoàn tác.</p>
            </div>

            {deleteGroupError && (
              <div className="auth-alert error" role="alert" style={{ marginTop: 14 }}>
                <span>⚠</span> {deleteGroupError}
              </div>
            )}

            <div className="confirm-modal-actions">
              <button
                type="button"
                className="link-btn cancel-confirm-btn"
                onClick={() => setIsDeleteGroupOpen(false)}
                disabled={deleteGroupLoading}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                className="danger-confirm-btn"
                onClick={handleExecuteDeleteGroup}
                disabled={deleteGroupLoading}
              >
                {deleteGroupLoading ? 'Đang xóa...' : 'Xác nhận xóa nhóm'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

