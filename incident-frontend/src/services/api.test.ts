import axios from 'axios';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
    API_BASE_URL,
    BACKEND_BASE_URL,
    clearTokens,
    getAccessToken,
    refreshSession,
    setAccessToken,
} from './api';

describe('API session state', () => {
    afterEach(() => {
        clearTokens();
        vi.restoreAllMocks();
    });

    it('keeps the access token in memory and clears it explicitly', () => {
        expect(getAccessToken()).toBeNull();

        setAccessToken('short-lived-access-token');
        expect(getAccessToken()).toBe('short-lived-access-token');

        clearTokens();
        expect(getAccessToken()).toBeNull();
    });

    it('derives the WebSocket backend origin from the API base URL', () => {
        expect(API_BASE_URL.endsWith('/api')).toBe(true);
        expect(BACKEND_BASE_URL).toBe(API_BASE_URL.slice(0, -4));
    });

    it('deduplicates concurrent refresh requests', async () => {
        const payload = { token: 'rotated-token', username: 'analyst', roles: ['ROLE_ANALYST'] };
        const post = vi.spyOn(axios, 'post').mockResolvedValue({ data: payload });

        const [first, second] = await Promise.all([refreshSession(), refreshSession()]);

        expect(post).toHaveBeenCalledTimes(1);
        expect(first).toEqual(payload);
        expect(second).toEqual(payload);
        expect(getAccessToken()).toBe(payload.token);
    });
});
