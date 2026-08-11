import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';

import MainLayout from './components/MainLayout';
import IncidentListPage from './pages/IncidentListPage';
import IncidentCreatePage from './pages/IncidentCreatePage';
import IncidentDetailPage from './pages/IncidentDetailPage';

// Dummy component placeholder cho dashboard
const DashboardPlaceholder = () => (
    <div>
        <h1>Dashboard</h1>
        <p>Tính năng đang được phát triển...</p>
    </div>
);

// PrivateRoute wrapper
const PrivateRoute = ({ children }: { children: React.ReactNode }) => {
    const { isAuthenticated } = useAuth();
    return isAuthenticated ? <MainLayout>{children}</MainLayout> : <Navigate to="/login" />;
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
                <Route path="/incidents" element={
                    <PrivateRoute>
                        <IncidentListPage />
                    </PrivateRoute>
                } />
                <Route path="/incidents/new" element={
                    <PrivateRoute>
                        <IncidentCreatePage />
                    </PrivateRoute>
                } />
                <Route path="/incidents/:id" element={
                    <PrivateRoute>
                        <IncidentDetailPage />
                    </PrivateRoute>
                } />
                <Route path="*" element={<Navigate to="/dashboard" />} />
            </Routes>
        </BrowserRouter>
    );
};

export default App;
