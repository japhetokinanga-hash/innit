<?php
// Usage: php tools/create_admin.php <username> <password>
if (PHP_SAPI !== 'cli') { exit('CLI only'); }
require __DIR__ . '/../includes/functions.php';
[$_, $user, $pass] = $argv + [null, null, null];
if (!$user || !$pass || strlen($pass) < 8) { exit("Usage: php create_admin.php <username> <password (8+ chars)>\n"); }
db()->prepare('INSERT INTO admin_users (username,password_hash) VALUES (?,?)
               ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash)')
    ->execute([$user, password_hash($pass, PASSWORD_DEFAULT)]);
echo "Admin '$user' saved.\n";
