<?php
// Override with environment variables in production.
define('DB_HOST', getenv('SP_DB_HOST') ?: '127.0.0.1');
define('DB_NAME', getenv('SP_DB_NAME') ?: 'shambapulse');
define('DB_USER', getenv('SP_DB_USER') ?: 'root');
define('DB_PASS', getenv('SP_DB_PASS') ?: '');
define('USSD_CODE', getenv('SP_USSD_CODE') ?: '*384#');
// Where the Java gateway is reachable from the browser (used by the USSD simulator).
define('GATEWAY_URL', getenv('SP_GATEWAY_URL') ?: 'http://localhost:8081');
