import React, { useState } from 'react';
import { login, register } from '../api/auth';
import type { AuthUser } from '../types/auth';

interface AuthModalProps {
  onSuccess: (user: AuthUser) => void;
}

export function AuthModal({ onSuccess }: AuthModalProps) {
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  function resetForm() {
    setError(null);
    setSuccessMessage(null);
  }

  function switchToLogin(prefillUsername?: string) {
    setMode('login');
    resetForm();
    if (prefillUsername) {
      setUsername(prefillUsername);
    }
    setPassword('');
  }

  function switchToRegister() {
    setMode('register');
    resetForm();
    setPassword('');
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSuccessMessage(null);

    const trimmedUsername = username.trim();
    const trimmedEmail = email.trim();

    if (!trimmedUsername) {
      setError('Vui lòng nhập tên đăng nhập');
      return;
    }

    if (!password) {
      setError('Vui lòng nhập mật khẩu');
      return;
    }

    if (mode === 'register') {
      if (!trimmedEmail) {
        setError('Vui lòng nhập email');
        return;
      }
      if (password.length < 6) {
        setError('Mật khẩu phải có ít nhất 6 ký tự');
        return;
      }
    }

    setLoading(true);

    try {
      if (mode === 'login') {
        const response = await login({
          username: trimmedUsername,
          password,
        });

        if (response.success && response.token && response.username) {
          onSuccess({
            token: response.token,
            username: response.username,
          });
        } else {
          setError(response.message || 'Đăng nhập không thành công');
        }
      } else {
        const response = await register({
          username: trimmedUsername,
          email: trimmedEmail,
          password,
        });

        if (response.success) {
          setSuccessMessage(response.message || 'Đăng ký thành công! Vui lòng đăng nhập.');
          // Đăng ký backend không cấp JWT -> chuyển về màn hình đăng nhập
          switchToLogin(trimmedUsername);
          setSuccessMessage('Đăng ký thành công! Hãy nhập mật khẩu để đăng nhập.');
        } else {
          setError(response.message || 'Đăng ký không thành công');
        }
      }
    } catch (err: any) {
      setError(err?.message || 'Đã có lỗi xảy ra. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="auth-overlay">
      <div className="auth-card" role="dialog" aria-modal="true" aria-labelledby="auth-title">
        <div className="auth-brand">
          <div className="brand-mark">z.</div>
          <div className="wordmark">
            CHAT<span>ZUN</span>
          </div>
          <p className="eyebrow">Your little corner</p>
        </div>

        <h2 id="auth-title" className="auth-title">
          {mode === 'login' ? 'Chào mừng trở lại' : 'Tạo tài khoản mới'}
        </h2>
        <p className="auth-desc">
          {mode === 'login'
            ? 'Đăng nhập để kết nối với những cuộc trò chuyện của bạn.'
            : 'Tham gia không gian trò chuyện ấm cúng cùng CHATZUN.'}
        </p>

        {error && (
          <div className="auth-alert error" role="alert">
            <span>⚠</span> {error}
          </div>
        )}

        {successMessage && (
          <div className="auth-alert success" role="status">
            <span>✓</span> {successMessage}
          </div>
        )}

        <form className="auth-form" onSubmit={handleSubmit}>
          <label className="auth-field">
            <span className="field-label">Tên đăng nhập</span>
            <input
              type="text"
              autoComplete="username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="ví dụ: zunn, dung"
              disabled={loading}
              required
            />
          </label>

          {mode === 'register' && (
            <label className="auth-field">
              <span className="field-label">Email</span>
              <input
                type="email"
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="tenban@domain.com"
                disabled={loading}
                required
              />
            </label>
          )}

          <label className="auth-field">
            <span className="field-label">Mật khẩu</span>
            <input
              type="password"
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder={mode === 'register' ? 'Tối thiểu 6 ký tự' : 'Nhập mật khẩu'}
              disabled={loading}
              required
            />
          </label>

          <button className="auth-submit-btn" type="submit" disabled={loading}>
            {loading ? 'Đang xử lý...' : mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'}
          </button>
        </form>

        <div className="auth-switch">
          {mode === 'login' ? (
            <p>
              Chưa có tài khoản?{' '}
              <button
                type="button"
                className="link-btn"
                onClick={switchToRegister}
                disabled={loading}
              >
                Đăng ký ngay
              </button>
            </p>
          ) : (
            <p>
              Đã có tài khoản?{' '}
              <button
                type="button"
                className="link-btn"
                onClick={() => switchToLogin()}
                disabled={loading}
              >
                Đăng nhập
              </button>
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
