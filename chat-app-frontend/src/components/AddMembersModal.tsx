import React, { useEffect, useRef, useState } from 'react';
import { addMembersToGroup, searchUsers } from '../api/chat';
import type { MemberResponse, UserSearchResult } from '../types/chat';

interface AddMembersModalProps {
  conversationId: number;
  groupName: string;
  existingMemberUsernames: string[];
  currentUsername: string;
  onClose: () => void;
  onMembersAdded: (updatedMembers: MemberResponse[]) => void;
}

export function AddMembersModal({
  conversationId,
  groupName,
  existingMemberUsernames,
  currentUsername,
  onClose,
  onMembersAdded,
}: AddMembersModalProps) {
  const [memberInput, setMemberInput] = useState('');
  const [selectedMembers, setSelectedMembers] = useState<string[]>([]);
  const [suggestions, setSuggestions] = useState<UserSearchResult[]>([]);
  const [searchPage, setSearchPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const debounceRef = useRef<number | null>(null);
  const requestVersionRef = useRef(0);

  useEffect(() => {
    const q = memberInput.trim();
    const requestVersion = ++requestVersionRef.current;
    if (!q) return;

    if (debounceRef.current) {
      window.clearTimeout(debounceRef.current);
    }

    debounceRef.current = window.setTimeout(async () => {
      try {
        const resultPage = await searchUsers(q);
        if (requestVersion !== requestVersionRef.current) return;
        const lowerExisting = new Set(
          existingMemberUsernames.map((u) => u.toLowerCase())
        );
        const lowerSelected = new Set(
          selectedMembers.map((u) => u.toLowerCase())
        );

        setSuggestions(
          resultPage.content.filter((user) => {
            const low = user.username.toLowerCase();
            return (
              low !== currentUsername.toLowerCase() &&
              !lowerExisting.has(low) &&
              !lowerSelected.has(low)
            );
          })
        );
        setSearchPage(resultPage.number);
        setHasMore(!resultPage.last);
      } catch {
        // bỏ qua lỗi gợi ý
      }
    }, 250);

    return () => {
      if (debounceRef.current) {
        window.clearTimeout(debounceRef.current);
      }
    };
  }, [memberInput, currentUsername, existingMemberUsernames, selectedMembers]);

  async function loadMoreSuggestions() {
    if (loadingMore || !memberInput.trim() || !hasMore) return;
    const requestVersion = requestVersionRef.current;
    setLoadingMore(true);
    try {
      const resultPage = await searchUsers(memberInput, searchPage + 1);
      if (requestVersion !== requestVersionRef.current) return;
      const existingIds = new Set(suggestions.map((user) => user.id));
      const lowerExisting = new Set(existingMemberUsernames.map((username) => username.toLowerCase()));
      const lowerSelected = new Set(selectedMembers.map((username) => username.toLowerCase()));
      setSuggestions((previous) => [
        ...previous,
        ...resultPage.content.filter((user) => {
          const lowerUsername = user.username.toLowerCase();
          return !existingIds.has(user.id) &&
            lowerUsername !== currentUsername.toLowerCase() &&
            !lowerExisting.has(lowerUsername) &&
            !lowerSelected.has(lowerUsername);
        }),
      ]);
      setSearchPage(resultPage.number);
      setHasMore(!resultPage.last);
    } catch {
      // Keep the current results available if loading another page fails.
    } finally {
      if (requestVersion === requestVersionRef.current) setLoadingMore(false);
    }
  }

  function handleAddMember(usernameToAdd: string) {
    const trimmed = usernameToAdd.trim();
    if (!trimmed) return;

    const lowerTrimmed = trimmed.toLowerCase();
    if (existingMemberUsernames.some((m) => m.toLowerCase() === lowerTrimmed)) {
      setError(`Người dùng "${trimmed}" đã là thành viên của nhóm.`);
      return;
    }

    if (selectedMembers.some((m) => m.toLowerCase() === lowerTrimmed)) {
      setError(`Người dùng "${trimmed}" đã có trong danh sách chọn.`);
      return;
    }

    setSelectedMembers((prev) => [...prev, trimmed]);
    setMemberInput('');
    setSuggestions([]);
    setSearchPage(0);
    setHasMore(false);
    setShowSuggestions(false);
    setError(null);
  }

  function handleRemoveMember(usernameToRemove: string) {
    setSelectedMembers((prev) =>
      prev.filter((m) => m.toLowerCase() !== usernameToRemove.toLowerCase())
    );
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (selectedMembers.length === 0) {
      setError('Vui lòng chọn ít nhất một thành viên để thêm.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const updatedList = await addMembersToGroup(conversationId, selectedMembers);
      onMembersAdded(updatedList);
      onClose();
    } catch (err: any) {
      setError(err?.message || 'Không thể thêm thành viên vào nhóm.');
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
        aria-labelledby="add-members-title"
      >
        <div className="details-head" style={{ marginBottom: 12 }}>
          <span className="eyebrow">Thêm thành viên</span>
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

        <h3 id="add-members-title" style={{ margin: '0 0 4px', fontSize: 18 }}>
          Thêm người vào nhóm "{groupName}"
        </h3>
        <p className="auth-desc" style={{ marginTop: 0, marginBottom: 16 }}>
          Mọi thành viên đều có thể thêm bạn bè vào nhóm trò chuyện này.
        </p>

        {error && (
          <div className="auth-alert error" role="alert" style={{ marginBottom: 14 }}>
            <span>⚠</span> {error}
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="auth-field" style={{ position: 'relative' }}>
            <span className="field-label">Tìm kiếm người dùng</span>
            <div className="member-input-row">
              <input
                type="text"
                autoFocus
                value={memberInput}
                onChange={(e) => {
                  const val = e.target.value;
                  setMemberInput(val);
                  setSuggestions([]);
                  setSearchPage(0);
                  setHasMore(false);
                  setShowSuggestions(true);
                  setError(null);
                }}
                onFocus={() => setShowSuggestions(true)}
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

            {showSuggestions && (suggestions.length > 0 || hasMore) && (
              <ul className="suggestions-dropdown">
                {suggestions.map((user) => (
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
                {hasMore && (
                  <li>
                    <button type="button" className="link-btn" onClick={loadMoreSuggestions} disabled={loadingMore}>
                      {loadingMore ? 'Đang tải...' : 'Tải thêm kết quả'}
                    </button>
                  </li>
                )}
              </ul>
            )}
          </div>

          {/* Danh sách thành viên sẽ được thêm */}
          <div className="selected-members-section">
            <span className="members-count-label">
              Thành viên sẽ được thêm ({selectedMembers.length}):
            </span>
            {selectedMembers.length === 0 ? (
              <p style={{ margin: '6px 0', fontSize: 12, color: 'var(--muted)', fontStyle: 'italic' }}>
                Chưa có ai được chọn. Tìm kiếm và bấm "＋ Thêm" ở trên.
              </p>
            ) : (
              <div className="member-chips-container">
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
            )}
          </div>

          <div className="confirm-modal-actions" style={{ marginTop: 20 }}>
            <button
              type="button"
              className="link-btn cancel-confirm-btn"
              onClick={onClose}
              disabled={loading}
            >
              Hủy bỏ
            </button>
            <button
              className="auth-submit-btn"
              type="submit"
              disabled={loading || selectedMembers.length === 0}
              style={{ width: 'auto', padding: '9px 20px', marginTop: 0 }}
            >
              {loading
                ? 'Đang thêm...'
                : `Thêm ${selectedMembers.length} thành viên`}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
