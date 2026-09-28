import { createContext, useContext } from 'react';

export interface AuthUser { username: string; roles: string[]; }

export interface AuthContextType {
    user: AuthUser | null;
    login: (token: string, username: string, roles: string[]) => void;
    logout: () => Promise<void>;
    isAuthenticated: boolean;
    isInitializing: boolean;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) throw new Error('useAuth must be used within an AuthProvider');
    return context;
};
