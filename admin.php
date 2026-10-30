<?php
// Officer JSON API.  GET ?action=session | dashboard   POST ?action=login|logout|new_alert|queue_alert|new_price
require __DIR__ . '/../includes/functions.php';
header('Content-Type: application/json; charset=utf-8');

function out(int $code, array $body): never { http_response_code($code); echo json_encode($body, JSON_UNESCAPED_UNICODE); exit; }
function need_admin(): void { if (empty($_SESSION['admin_id'])) out(401, ['ok' => false, 'error' => 'Please log in.']); }

$action = $_GET['action'] ?? '';
$method = $_SERVER['REQUEST_METHOD'];
$in = json_decode(file_get_contents('php://input'), true) ?: [];
$pdo = db();

if ($action === 'session') out(200, ['loggedIn' => !empty($_SESSION['admin_id']), 'csrf' => csrf_token()]);

if ($action === 'dashboard' && $method === 'GET') {
    need_admin();
    out(200, [
        'ok' => true,
        'counties' => $pdo->query('SELECT id, name FROM counties ORDER BY name')->fetchAll(),
        'subscribers' => $pdo->query("SELECT c.name, COUNT(f.id) AS n FROM counties c
            LEFT JOIN wards w ON w.county_id=c.id LEFT JOIN farmers f ON f.ward_id=w.id AND f.subscribed=1
            GROUP BY c.id ORDER BY c.name")->fetchAll(),
        'alerts' => $pdo->query("SELECT a.id, a.type, a.status, a.body_en, c.name AS county,
            (SELECT COUNT(*) FROM alert_deliveries d WHERE d.alert_id=a.id) AS total,
            (SELECT COUNT(*) FROM alert_deliveries d WHERE d.alert_id=a.id AND d.status='delivered') AS delivered,
            (SELECT COUNT(*) FROM alert_deliveries d WHERE d.alert_id=a.id AND d.status='failed') AS failed
            FROM alerts a JOIN counties c ON c.id=a.county_id ORDER BY a.id DESC LIMIT 20")->fetchAll(),
        'messages' => $pdo->query('SELECT created_at, full_name, email, role, message FROM contact_messages ORDER BY id DESC LIMIT 20')->fetchAll(),
    ]);
}

if ($method !== 'POST') out(405, ['ok' => false, 'error' => 'POST only.']);
$tok = $_SERVER['HTTP_X_CSRF_TOKEN'] ?? '';
if (empty($_SESSION['csrf']) || !hash_equals($_SESSION['csrf'], $tok)) out(403, ['ok' => false, 'error' => 'Session expired. Reload the page.']);

try {
    switch ($action) {
        case 'login':
            $st = $pdo->prepare('SELECT id, password_hash FROM admin_users WHERE username = ?');
            $st->execute([trim($in['username'] ?? '')]);
            $u = $st->fetch();
            if ($u && password_verify($in['password'] ?? '', $u['password_hash'])) {
                session_regenerate_id(true);
                $_SESSION['admin_id'] = (int)$u['id'];
                out(200, ['ok' => true, 'csrf' => csrf_token()]);
            }
            sleep(1); // slow down guessing
            out(401, ['ok' => false, 'error' => 'Wrong username or password.']);
        case 'logout':
            $_SESSION = []; session_destroy();
            out(200, ['ok' => true]);
        case 'new_alert':
            need_admin();
            $en = trim($in['body_en'] ?? ''); $sw = trim($in['body_sw'] ?? ''); $type = $in['type'] ?? '';
            if (!in_array($type, ['weather', 'advisory', 'market'], true)) out(422, ['ok' => false, 'error' => 'Pick a valid alert type.']);
            if ($en === '' || $sw === '') out(422, ['ok' => false, 'error' => 'Write the alert in both English and Kiswahili.']);
            if (mb_strlen($en) > 160 || mb_strlen($sw) > 160) out(422, ['ok' => false, 'error' => 'Each message must fit one SMS (160 characters).']);
            $send = !empty($in['send_now']);
            $pdo->prepare('INSERT INTO alerts (county_id,type,body_en,body_sw,status,created_by) VALUES (?,?,?,?,?,?)')
                ->execute([(int)($in['county_id'] ?? 0), $type, $en, $sw, $send ? 'queued' : 'draft', $_SESSION['admin_id']]);
            out(200, ['ok' => true, 'message' => $send ? 'Alert queued. The gateway will send it within seconds.' : 'Draft saved.']);
        case 'queue_alert':
            need_admin();
            $pdo->prepare("UPDATE alerts SET status='queued' WHERE id=? AND status='draft'")->execute([(int)($in['id'] ?? 0)]);
            out(200, ['ok' => true, 'message' => 'Alert queued for sending.']);
        case 'new_price':
            need_admin();
            $price = (float)($in['price_kes'] ?? 0);
            $date = $in['price_date'] ?? '';
            if ($price <= 0) out(422, ['ok' => false, 'error' => 'Price must be above zero.']);
            if (!preg_match('/^\d{4}-\d{2}-\d{2}$/', $date)) out(422, ['ok' => false, 'error' => 'Choose a valid date.']);
            $pdo->prepare('INSERT INTO prices (commodity,unit,market,price_date,price_kes,source) VALUES (?,?,?,?,?,?)
                           ON DUPLICATE KEY UPDATE price_kes=VALUES(price_kes), source=VALUES(source)')
                ->execute([trim($in['commodity'] ?? ''), trim($in['unit'] ?? ''), trim($in['market'] ?? ''), $date, $price, 'officer entry']);
            out(200, ['ok' => true, 'message' => 'Price saved.']);
        default:
            out(404, ['ok' => false, 'error' => 'Unknown action.']);
    }
} catch (PDOException $ex) {
    out(500, ['ok' => false, 'error' => 'Could not save. Check your input.']);
}
