import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/AuthContext';
import LoginPage from './pages/LoginPage';

import MainLayout from './components/MainLayout';
import IncidentListPage from './pages/IncidentListPage';
import IncidentCreatePage from './pages/IncidentCreatePage';
import IncidentDetailPage from './pages/IncidentDetailPage';
import UserManagementPage from './pages/UserManagementPage';

import DashboardPage from './pages/DashboardPage';

// PrivateRoute wrapper
const PrivateRoute = ({ children, allowedRoles }: { children: React.ReactNode; allowedRoles?: string[] }) => {
    const { isAuthenticated, user } = useAuth();
    if (!isAuthenticated) return <Navigate to="/login" />;
    if (allowedRoles && !user?.roles.some(role => allowedRoles.includes(role))) {
        return <Navigate to="/incidents" replace />;
    }
    return <MainLayout>{children}</MainLayout>;
};

const App: React.FC = () => {
    return (
        <BrowserRouter>
            <Routes>
                <Route path="/login" element={<LoginPage />} />
                <Route path="/dashboard" element={
                    <PrivateRoute allowedRoles={['ROLE_ADMIN', 'ROLE_MANAGER']}>
                        <DashboardPage />
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
                <Route path="/users" element={
                    <PrivateRoute allowedRoles={['ROLE_ADMIN']}>
                        <UserManagementPage />
                    </PrivateRoute>
                } />
                <Route path="*" element={<Navigate to="/dashboard" />} />
            </Routes>
        </BrowserRouter>
    );
};

export default App;
