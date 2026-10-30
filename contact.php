<?php
// POST JSON {name,email,role,message,website}  header X-CSRF-Token
require __DIR__ . '/../includes/functions.php';
header('Content-Type: application/json; charset=utf-8');
function out(int $code, array $body) { http_response_code($code); echo json_encode($body); exit; }
if ($_SERVER['REQUEST_METHOD'] !== 'POST') out(405, ['ok' => false, 'errors' => ['POST only']]);
$in = json_decode(file_get_contents('php://input'), true) ?: [];
$tok = $_SERVER['HTTP_X_CSRF_TOKEN'] ?? '';
if (empty($_SESSION['csrf']) || !hash_equals($_SESSION['csrf'], $tok)) out(403, ['ok' => false, 'errors' => ['Session expired. Reload the page.']]);
if (!empty($in['website'])) out(200, ['ok' => true]); // honeypot
$name = trim($in['name'] ?? ''); $email = trim($in['email'] ?? '');
$role = $in['role'] ?? 'Other'; $msg = trim($in['message'] ?? '');
$errors = [];
if ($name === '' || mb_strlen($name) > 100) $errors[] = 'Please enter your full name.';
if (!filter_var($email, FILTER_VALIDATE_EMAIL)) $errors[] = 'Please enter a valid email address.';
if (!in_array($role, ['Farmer','Buyer','Partner or organisation','Other'], true)) $role = 'Other';
if ($msg === '' || mb_strlen($msg) > 2000) $errors[] = 'Please write a message (up to 2000 characters).';
if ($errors) out(422, ['ok' => false, 'errors' => $errors]);
db()->prepare('INSERT INTO contact_messages (full_name,email,role,message) VALUES (?,?,?,?)')->execute([$name,$email,$role,$msg]);
out(200, ['ok' => true, 'message' => 'Thank you! We will reply within two working days.']);
