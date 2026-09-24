-- V6: Tai khoan demo cho README va moi truong trinh dien

INSERT INTO users (username, password, email, full_name, department, enabled)
SELECT 'manager', '$2a$10$G7.JI2dof33Wmcy2NEYATO6lbXl2c6TQigAx/47tgbHDq9IXSJEUu',
       'manager@example.com', 'Quan ly ATTT', 'IT/ATTT', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'manager');

INSERT INTO users (username, password, email, full_name, department, enabled)
SELECT 'analyst', '$2a$10$G7.JI2dof33Wmcy2NEYATO6lbXl2c6TQigAx/47tgbHDq9IXSJEUu',
       'analyst@example.com', 'Chuyen vien phan tich', 'IT/ATTT', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'analyst');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'MANAGER'
WHERE u.username = 'manager'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
JOIN roles r ON r.name = 'ANALYST'
WHERE u.username = 'analyst'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );