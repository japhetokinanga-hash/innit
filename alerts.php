<?php
// GET /api/alerts.php?county=Kisii -> JSON of sent alerts
require __DIR__ . '/../includes/functions.php';
header('Content-Type: application/json; charset=utf-8');
$county = trim($_GET['county'] ?? '');
$sql = "SELECT a.type, a.body_en, a.body_sw, a.sent_at, c.name AS county
        FROM alerts a JOIN counties c ON c.id = a.county_id WHERE a.status = 'sent'";
$args = [];
if ($county !== '') { $sql .= ' AND c.name = ?'; $args[] = $county; }
$sql .= ' ORDER BY a.sent_at DESC LIMIT 30';
$st = db()->prepare($sql); $st->execute($args);
$counties = db()->query('SELECT name FROM counties ORDER BY name')->fetchAll(PDO::FETCH_COLUMN);
echo json_encode(['alerts' => $st->fetchAll(), 'counties' => $counties], JSON_UNESCAPED_UNICODE);
