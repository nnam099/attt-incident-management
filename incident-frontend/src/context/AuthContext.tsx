import { useCallback, useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { clearTokens, refreshSession, revokeSession, setAccessToken } from '../services/api';
import { AuthContext } from './auth';
import type { AuthUser } from './auth';

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [user, setUser] = useState<AuthUser | null>(null);
    const [isInitializing, setIsInitializing] = useState(true);

    const login = useCallback((token: string, username: string, roles: string[]) => {
        setAccessToken(token);
        setUser({ username, roles });
    }, []);

    const logout = useCallback(async () => {
        try {
            await revokeSession();
        } finally {
            clearTokens();
            setUser(null);
        }
    }, []);

    useEffect(() => {
        let active = true;
        refreshSession()
            .then(auth => { if (active) setUser({ username: auth.username, roles: auth.roles }); })
            .catch(() => clearTokens())
            .finally(() => { if (active) setIsInitializing(false); });

        const expire = () => setUser(null);
        window.addEventListener('auth-expired', expire);
        return () => {
            active = false;
            window.removeEventListener('auth-expired', expire);
        };
    }, []);

    return (
        <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user, isInitializing }}>
            {children}
        </AuthContext.Provider>
    );
};
