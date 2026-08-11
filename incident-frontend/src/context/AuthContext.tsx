import { createContext, useContext, useState } from 'react';
import type { ReactNode } from 'react';
import { setTokens, clearTokens } from '../services/api';

interface User {
    username: string;
    roles: string[];
}

interface AuthContextType {
    user: User | null;
    login: (token: string, refreshToken: string, username: string, roles: string[]) => void;
    logout: () => void;
    isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
    const [user, setUser] = useState<User | null>(null);

    const login = (token: string, refreshToken: string, username: string, roles: string[]) => {
        setTokens(token, refreshToken);
        setUser({ username, roles });
    };

    const logout = () => {
        clearTokens();
        setUser(null);
    };

    return (
        <AuthContext.Provider value={{ user, login, logout, isAuthenticated: !!user }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (context === undefined) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};
