<?php
// GET -> {"token": "..."} used by the contact form (same session as api/contact.php)
require __DIR__ . '/../includes/functions.php';
header('Content-Type: application/json');
echo json_encode(['token' => csrf_token()]);
