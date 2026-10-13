import React, { useEffect, useRef, useState } from 'react';
import { createDirectConversation, createGroupConversation, searchUsers } from '../api/chat';
import type { ConversationResponse, UserSearchResult } from '../types/chat';

interface NewConversationModalProps {
  onClose: () => void;
  onCreated: (conversation: ConversationResponse) => void;
  currentUsername: string;
}

type TabMode = 'direct' | 'group';

export function NewConversationModal({
  onClose,
  onCreated,
  currentUsername,
}: NewConversationModalProps) {
  const [mode, setMode] = useState<TabMode>('direct');

  // State cho cuộc trò chuyện cá nhân
  const [directUsername, setDirectUsername] = useState('');
  const [directSuggestions, setDirectSuggestions] = useState<UserSearchResult[]>([]);
  const [directSearchPage, setDirectSearchPage] = useState(0);
  const [directHasMore, setDirectHasMore] = useState(false);
  const [loadingMoreDirect, setLoadingMoreDirect] = useState(false);
  const [showDirectSuggestions, setShowDirectSuggestions] = useState(false);

  // State cho tạo nhóm
  const [groupName, setGroupName] = useState('');
  const [memberInput, setMemberInput] = useState('');
  const [selectedMembers, setSelectedMembers] = useState<string[]>([]);
  const [groupSuggestions, setGroupSuggestions] = useState<UserSearchResult[]>([]);
  const [groupSearchPage, setGroupSearchPage] = useState(0);
  const [groupHasMore, setGroupHasMore] = useState(false);
  const [loadingMoreGroup, setLoadingMoreGroup] = useState(false);
  const [showGroupSuggestions, setShowGroupSuggestions] = useState(false);

  // Trạng thái chung
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const directDebounceRef = useRef<number | null>(null);
  const groupDebounceRef = useRef<number | null>(null);
  const directRequestVersionRef = useRef(0);
  const groupRequestVersionRef = useRef(0);

  // Tìm kiếm gợi ý người dùng khi nhập ở tab Cá nhân
  useEffect(() => {
    const q = directUsername.trim();
    const requestVersion = ++directRequestVersionRef.current;
    if (!q) return;

    if (directDebounceRef.current) {
      window.clearTimeout(directDebounceRef.current);
    }

    directDebounceRef.current = window.setTimeout(async () => {
      try {
        const resultPage = await searchUsers(q);
        if (requestVersion !== directRequestVersionRef.current) return;
        setDirectSuggestions(resultPage.content.filter(
          (user) => user.username.toLowerCase() !== currentUsername.toLowerCase()
        ));
        setDirectSearchPage(resultPage.number);
        setDirectHasMore(!resultPage.last);
      } catch {
        // bỏ qua lỗi gợi ý
      }
    }, 250);

    return () => {
      if (directDebounceRef.current) {
        window.clearTimeout(directDebounceRef.current);
      }
    };
  }, [directUsername, currentUsername]);

  // Tìm kiếm gợi ý người dùng khi nhập ở ô thêm thành viên nhóm
  useEffect(() => {
    const q = memberInput.trim();
    const requestVersion = ++groupRequestVersionRef.current;
    if (!q) return;

    if (groupDebounceRef.current) {
      window.clearTimeout(groupDebounceRef.current);
    }

    groupDebounceRef.current = window.setTimeout(async () => {
      try {
        const resultPage = await searchUsers(q);
        if (requestVersion !== groupRequestVersionRef.current) return;
        setGroupSuggestions(
          resultPage.content.filter(
            (user) =>
              user.username.toLowerCase() !== currentUsername.toLowerCase() &&
              !selectedMembers.some((member) => member.toLowerCase() === user.username.toLowerCase())
          )
        );
        setGroupSearchPage(resultPage.number);
        setGroupHasMore(!resultPage.last);
      } catch {
        // bỏ qua lỗi gợi ý
      }
    }, 250);

    return () => {
      if (groupDebounceRef.current) {
        window.clearTimeout(groupDebounceRef.current);
      }
    };
  }, [memberInput, currentUsername, selectedMembers]);

  async function loadMoreDirectSuggestions() {
    if (loadingMoreDirect || !directUsername.trim() || !directHasMore) return;
    const requestVersion = directRequestVersionRef.current;
    setLoadingMoreDirect(true);
    try {
      const resultPage = await searchUsers(directUsername, directSearchPage + 1);
      if (requestVersion !== directRequestVersionRef.current) return;
      const existingIds = new Set(directSuggestions.map((user) => user.id));
      setDirectSuggestions((previous) => [
        ...previous,
        ...resultPage.content.filter(
          (user) =>
            !existingIds.has(user.id) &&
            user.username.toLowerCase() !== currentUsername.toLowerCase()
        ),
      ]);
      setDirectSearchPage(resultPage.number);
      setDirectHasMore(!resultPage.last);
    } catch {
      // Keep the current results available if loading another page fails.
    } finally {
      if (requestVersion === directRequestVersionRef.current) setLoadingMoreDirect(false);
    }
  }

  async function loadMoreGroupSuggestions() {
    if (loadingMoreGroup || !memberInput.trim() || !groupHasMore) return;
    const requestVersion = groupRequestVersionRef.current;
    setLoadingMoreGroup(true);
    try {
      const resultPage = await searchUsers(memberInput, groupSearchPage + 1);
      if (requestVersion !== groupRequestVersionRef.current) return;
      const existingIds = new Set(groupSuggestions.map((user) => user.id));
      setGroupSuggestions((previous) => [
        ...previous,
        ...resultPage.content.filter(
          (user) =>
            !existingIds.has(user.id) &&
            user.username.toLowerCase() !== currentUsername.toLowerCase() &&
            !selectedMembers.some((member) => member.toLowerCase() === user.username.toLowerCase())
        ),
      ]);
      setGroupSearchPage(resultPage.number);
      setGroupHasMore(!resultPage.last);
    } catch {
      // Keep the current results available if loading another page fails.
    } finally {
      if (requestVersion === groupRequestVersionRef.current) setLoadingMoreGroup(false);
    }
  }

  // Thêm một thành viên vào danh sách nhóm
  function handleAddMember(usernameToAdd: string) {
    const trimmed = usernameToAdd.trim();
    if (!trimmed) return;

    if (trimmed.toLowerCase() === currentUsername.toLowerCase()) {
      setError('Bạn là người tạo nhóm, không cần tự thêm chính mình.');
      return;
    }

    if (selectedMembers.some((m) => m.toLowerCase() === trimmed.toLowerCase())) {
      setError(`Người dùng "${trimmed}" đã có trong danh sách.`);
      return;
    }

    setSelectedMembers((prev) => [...prev, trimmed]);
    setMemberInput('');
    setGroupSuggestions([]);
    setGroupSearchPage(0);
    setGroupHasMore(false);
    setShowGroupSuggestions(false);
    setError(null);
  }

  // Xóa một thành viên khỏi danh sách nhóm
  function handleRemoveMember(usernameToRemove: string) {
    setSelectedMembers((prev) =>
      prev.filter((m) => m.toLowerCase() !== usernameToRemove.toLowerCase())
    );
  }

  // Submit tạo cá nhân
  async function handleDirectSubmit(e: React.FormEvent) {
    e.preventDefault();
    const target = directUsername.trim();

    if (!target) {
      setError('Vui lòng nhập tên người dùng');
      return;
    }

    if (target.toLowerCase() === currentUsername.toLowerCase()) {
      setError('Không thể tạo cuộc trò chuyện với chính bạn');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const conversation = await createDirectConversation(target);
      onCreated(conversation);
    } catch (err: any) {
      setError(err?.message || 'Không thể tạo cuộc trò chuyện cá nhân');
    } finally {
      setLoading(false);
    }
  }

  // Submit tạo nhóm
  async function handleGroupSubmit(e: React.FormEvent) {
    e.preventDefault();
    const name = groupName.trim();

    if (!name) {
      setError('Vui lòng nhập tên nhóm');
      return;
    }

    if (selectedMembers.length === 0) {
      setError('Vui lòng chọn ít nhất một thành viên cho nhóm');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const conversation = await createGroupConversation(name, selectedMembers);
      onCreated(conversation);
    } catch (err: any) {
      setError(err?.message || 'Không thể tạo nhóm trò chuyện');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-overlay" onClick={onClose}>
      <div
        className="auth-card modal-wide"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="new-chat-title"
      >
        <div className="details-head" style={{ marginBottom: 12 }}>
          <span className="eyebrow">Cuộc trò chuyện mới</span>
          <button
            className="round-btn"
            type="button"
            onClick={onClose}
            aria-label="Đóng"
            disabled={loading}
          >
            ×
          </button>
        </div>

        {/* Chuyển đổi giữa 2 tab: Cá nhân vs Tạo nhóm */}
        <div className="modal-tabs" role="tablist">
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'direct'}
            className={`modal-tab-btn ${mode === 'direct' ? 'active' : ''}`}
            onClick={() => {
              setMode('direct');
              setError(null);
            }}
          >
            💬 Cá nhân
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={mode === 'group'}
            className={`modal-tab-btn ${mode === 'group' ? 'active' : ''}`}
            onClick={() => {
              setMode('group');
              setError(null);
            }}
          >
            👥 Tạo nhóm
          </button>
        </div>

        {error && (
          <div className="auth-alert error" role="alert" style={{ marginTop: 14 }}>
            <span>⚠</span> {error}
          </div>
        )}

        {/* TAB CÁ NHÂN */}
        {mode === 'direct' && (
          <form className="auth-form" onSubmit={handleDirectSubmit} style={{ marginTop: 14 }}>
            <p className="auth-desc" style={{ marginTop: 0 }}>
              Tìm kiếm username của người bạn muốn nhắn tin. Nếu đã có cuộc trò chuyện từ trước, hệ thống sẽ mở lại ngay.
            </p>

            <div className="auth-field" style={{ position: 'relative' }}>
              <span className="field-label">Tên người dùng (Username)</span>
              <input
                type="text"
                autoFocus
                value={directUsername}
                onChange={(e) => {
                  const val = e.target.value;
                  setDirectUsername(val);
                  setDirectSuggestions([]);
                  setDirectSearchPage(0);
                  setDirectHasMore(false);
                  setShowDirectSuggestions(true);
                  setError(null);
                }}
                onFocus={() => setShowDirectSuggestions(true)}
                placeholder="ví dụ: bob, alice, zunn..."
                disabled={loading}
                required
              />

              {showDirectSuggestions && (directSuggestions.length > 0 || directHasMore) && (
                <ul className="suggestions-dropdown">
                  {directSuggestions.map((user) => (
                    <li
                      key={user.id}
                      onClick={() => {
                        setDirectUsername(user.username);
                        setShowDirectSuggestions(false);
                      }}
                    >
                      <span className="sugg-avatar">{user.username.slice(0, 2).toUpperCase()}</span>
                      <span className="sugg-name">{user.username}</span>
                    </li>
                  ))}
                  {directHasMore && (
                    <li>
                      <button type="button" className="link-btn" onClick={loadMoreDirectSuggestions} disabled={loadingMoreDirect}>
                        {loadingMoreDirect ? 'Đang tải...' : 'Tải thêm kết quả'}
                      </button>
                    </li>
                  )}
                </ul>
              )}
            </div>

            <button
              className="auth-submit-btn"
              type="submit"
              disabled={loading || !directUsername.trim()}
            >
              {loading ? 'Đang kết nối...' : 'Bắt đầu trò chuyện'}
            </button>
          </form>
        )}

        {/* TAB TẠO NHÓM */}
        {mode === 'group' && (
          <form className="auth-form" onSubmit={handleGroupSubmit} style={{ marginTop: 14 }}>
            <p className="auth-desc" style={{ marginTop: 0 }}>
              Đặt tên nhóm và thêm các thành viên. Bạn sẽ là người quản trị nhóm (Admin).
            </p>

            <label className="auth-field">
              <span className="field-label">Tên nhóm</span>
              <input
                type="text"
                autoFocus
                value={groupName}
                onChange={(e) => {
                  setGroupName(e.target.value);
                  setError(null);
                }}
                placeholder="ví dụ: Nhóm Học Tập, Dev Team..."
                maxLength={100}
                disabled={loading}
                required
              />
            </label>

            <div className="auth-field" style={{ position: 'relative' }}>
              <span className="field-label">Thêm thành viên</span>
              <div className="member-input-row">
                <input
                  type="text"
                  value={memberInput}
                  onChange={(e) => {
                    const val = e.target.value;
                    setMemberInput(val);
                    setGroupSuggestions([]);
                    setGroupSearchPage(0);
                    setGroupHasMore(false);
                    setShowGroupSuggestions(true);
                    setError(null);
                  }}
                  onFocus={() => setShowGroupSuggestions(true)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      handleAddMember(memberInput);
                    }
                  }}
                  placeholder="Nhập username rồi bấm Thêm hoặc Enter..."
                  disabled={loading}
                />
                <button
                  type="button"
                  className="add-member-btn"
                  onClick={() => handleAddMember(memberInput)}
                  disabled={loading || !memberInput.trim()}
                >
                  ＋ Thêm
                </button>
              </div>

              {showGroupSuggestions && (groupSuggestions.length > 0 || groupHasMore) && (
                <ul className="suggestions-dropdown">
                  {groupSuggestions.map((user) => (
                    <li
                      key={user.id}
                      onClick={() => {
                        handleAddMember(user.username);
                      }}
                    >
                      <span className="sugg-avatar">{user.username.slice(0, 2).toUpperCase()}</span>
                      <span className="sugg-name">{user.username}</span>
                      <span className="sugg-badge">+ Thêm</span>
                    </li>
                  ))}
                  {groupHasMore && (
                    <li>
                      <button type="button" className="link-btn" onClick={loadMoreGroupSuggestions} disabled={loadingMoreGroup}>
                        {loadingMoreGroup ? 'Đang tải...' : 'Tải thêm kết quả'}
                      </button>
                    </li>
                  )}
                </ul>
              )}
            </div>

            {/* Danh sách thành viên đã chọn */}
            <div className="selected-members-section">
              <span className="members-count-label">
                Thành viên đã chọn ({selectedMembers.length}):
              </span>
              <div className="member-chips-container">
                <div className="member-chip creator-chip" title="Bạn là người tạo nhóm">
                  <span>{currentUsername} (Bạn)</span>
                </div>
                {selectedMembers.map((member) => (
                  <div key={member} className="member-chip">
                    <span>{member}</span>
                    <button
                      type="button"
                      className="chip-remove-btn"
                      onClick={() => handleRemoveMember(member)}
                      aria-label={`Bỏ chọn ${member}`}
                      disabled={loading}
                    >
                      ×
                    </button>
                  </div>
                ))}
              </div>
            </div>

            <button
              className="auth-submit-btn"
              type="submit"
              disabled={loading || !groupName.trim() || selectedMembers.length === 0}
            >
              {loading
                ? 'Đang tạo nhóm...'
                : `Tạo nhóm (${selectedMembers.length + 1} thành viên)`}
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
