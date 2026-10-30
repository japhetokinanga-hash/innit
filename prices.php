<?php
// GET /api/prices.php?q=maize&market=Kisumu  -> JSON
require __DIR__ . '/../includes/functions.php';
header('Content-Type: application/json; charset=utf-8');
$q = trim($_GET['q'] ?? ''); $market = trim($_GET['market'] ?? '');
$sql = 'SELECT commodity, unit, market, price_date, price_kes, source FROM prices WHERE 1=1';
$args = [];
if ($q !== '')      { $sql .= ' AND commodity LIKE ?'; $args[] = '%' . $q . '%'; }
if ($market !== '') { $sql .= ' AND market = ?';       $args[] = $market; }
$sql .= ' ORDER BY price_date DESC, commodity LIMIT 100';
$st = db()->prepare($sql); $st->execute($args);
$markets = db()->query('SELECT DISTINCT market FROM prices ORDER BY market')->fetchAll(PDO::FETCH_COLUMN);
echo json_encode(['prices' => $st->fetchAll(), 'markets' => $markets], JSON_UNESCAPED_UNICODE);
