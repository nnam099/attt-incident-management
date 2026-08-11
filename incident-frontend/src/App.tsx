import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';

// Dummy component placeholder cho dashboard
const DashboardPlaceholder = () => (
    <div style={{ padding: 24 }}>
        <h1>Dashboard</h1>
        <p>Tính năng đang được phát triển...</p>
    </div>
);

// PrivateRoute wrapper
const PrivateRoute = ({ children }: { children: React.ReactNode }) => {
    const { isAuthenticated } = useAuth();
    return isAuthenticated ? <>{children}</> : <Navigate to="/login" />;
};

const App: React.FC = () => {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/dashboard" element={
                    <PrivateRoute>
                        <DashboardPlaceholder />
                    </PrivateRoute>
                } />
                <Route path="*" element={<Navigate to="/dashboard" />} />
            </Routes>
        </BrowserRouter>
    );
};

export default App;
