import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from './context/auth';
import LoginPage from './pages/LoginPage';

import MainLayout from './components/MainLayout';
import { getHomePath } from './config/roleExperience';
const IncidentListPage = lazy(() => import('./pages/IncidentListPage'));
const IncidentCreatePage = lazy(() => import('./pages/IncidentCreatePage'));
const IncidentDetailPage = lazy(() => import('./pages/IncidentDetailPage'));
const UserManagementPage = lazy(() => import('./pages/UserManagementPage'));
const DashboardPage = lazy(() => import('./pages/DashboardPage'));

// PrivateRoute wrapper
const PrivateRoute = ({ children, allowedRoles }: { children: React.ReactNode; allowedRoles?: string[] }) => {
    const { isAuthenticated, isInitializing, user } = useAuth();
    if (isInitializing) return <div style={{ padding: 24 }}>Đang khôi phục phiên đăng nhập...</div>;
    if (!isAuthenticated) return <Navigate to="/login" />;
    if (allowedRoles && !user?.roles.some(role => allowedRoles.includes(role))) {
        return <Navigate to={getHomePath(user?.roles)} replace />;
    }
    return <MainLayout>{children}</MainLayout>;
};

const HomeRedirect = () => {
    const { user, isInitializing } = useAuth();
    if (isInitializing) return <div style={{ padding: 24 }}>Đang khôi phục phiên đăng nhập...</div>;
    return <Navigate to={user ? getHomePath(user.roles) : '/login'} replace />;
};

const App: React.FC = () => {
    return (
        <BrowserRouter>
            <Suspense fallback={<div style={{ padding: 24 }}>Đang tải...</div>}><Routes>
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
                <Route path="*" element={<HomeRedirect />} />
            </Routes></Suspense>
        </BrowserRouter>
    );
};

export default App;
