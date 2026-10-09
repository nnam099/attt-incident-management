import { describe, expect, it } from 'vitest';
import { getHomePath, getPrimaryRole } from './roleExperience';

describe('role workspaces', () => {
    it.each([
        ['ROLE_ADMIN', '/users'],
        ['ROLE_MANAGER', '/dashboard'],
        ['ROLE_HELPDESK', '/incidents?status=NEW'],
        ['ROLE_ANALYST', '/incidents?mine=ASSIGNED'],
        ['ROLE_REPORTER', '/incidents?mine=REPORTED'],
    ])('routes %s to its own starting workspace', (role, home) => {
        expect(getHomePath([role])).toBe(home);
    });

    it('uses the highest privilege for accounts with multiple roles', () => {
        expect(getPrimaryRole(['ROLE_REPORTER', 'ROLE_ADMIN'])).toBe('ADMIN');
    });
});
