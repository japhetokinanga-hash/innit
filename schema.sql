-- ShambaPulse database (MySQL 8 / MariaDB 10.5+)
CREATE DATABASE IF NOT EXISTS shambapulse CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE shambapulse;

CREATE TABLE counties (
  id TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(60) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE wards (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  county_id TINYINT UNSIGNED NOT NULL,
  name VARCHAR(80) NOT NULL,
  UNIQUE KEY uq_ward (county_id, name),
  FOREIGN KEY (county_id) REFERENCES counties(id)
) ENGINE=InnoDB;

-- A farmer row exists ONLY after consent (Data Protection Act 2019).
CREATE TABLE farmers (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  phone VARCHAR(20) NOT NULL UNIQUE,
  ward_id INT UNSIGNED NOT NULL,
  language ENUM('en','sw') NOT NULL DEFAULT 'en',
  consent_at DATETIME NOT NULL,
  subscribed TINYINT(1) NOT NULL DEFAULT 1,
  opted_out_at DATETIME NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (ward_id) REFERENCES wards(id)
) ENGINE=InnoDB;

CREATE TABLE alerts (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  county_id TINYINT UNSIGNED NOT NULL,
  type ENUM('weather','advisory','market') NOT NULL,
  body_en VARCHAR(160) NOT NULL,   -- one SMS
  body_sw VARCHAR(160) NOT NULL,
  status ENUM('draft','queued','sent') NOT NULL DEFAULT 'draft',
  created_by INT UNSIGNED NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  sent_at DATETIME NULL,
  FOREIGN KEY (county_id) REFERENCES counties(id)
) ENGINE=InnoDB;

-- "submitted" is not "delivered": the gateway updates status from delivery reports.
CREATE TABLE alert_deliveries (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  alert_id INT UNSIGNED NOT NULL,
  farmer_id INT UNSIGNED NOT NULL,
  status ENUM('submitted','delivered','failed') NOT NULL DEFAULT 'submitted',
  provider_ref VARCHAR(80) NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uq_delivery (alert_id, farmer_id),
  KEY idx_ref (provider_ref),
  FOREIGN KEY (alert_id) REFERENCES alerts(id),
  FOREIGN KEY (farmer_id) REFERENCES farmers(id)
) ENGINE=InnoDB;

CREATE TABLE prices (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  commodity VARCHAR(60) NOT NULL,
  unit VARCHAR(40) NOT NULL,
  market VARCHAR(80) NOT NULL,
  price_date DATE NOT NULL,
  price_kes DECIMAL(10,2) NOT NULL,
  source VARCHAR(80) NOT NULL DEFAULT 'manual entry',
  UNIQUE KEY uq_price (commodity, unit, market, price_date),  -- blocks duplicate imports
  KEY idx_date (price_date)
) ENGINE=InnoDB;

CREATE TABLE contact_messages (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  full_name VARCHAR(100) NOT NULL,
  email VARCHAR(150) NOT NULL,
  role ENUM('Farmer','Buyer','Partner or organisation','Other') NOT NULL,
  message TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE admin_users (
  id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

-- ---------- seed data ----------
INSERT INTO counties (name) VALUES ('Kisii'),('Kisumu'),('Migori');
INSERT INTO wards (county_id,name) VALUES
 (1,'Kitutu Central'),(1,'Bobasi'),(1,'Nyaribari'),
 (2,'Kisumu Central'),(2,'Nyando'),(2,'Muhoroni'),
 (3,'Rongo'),(3,'Awendo'),(3,'Uriri');

INSERT INTO prices (commodity,unit,market,price_date,price_kes,source) VALUES
 ('Maize','90 kg bag','Nairobi',CURDATE(),5000,'sample'),
 ('Beans','90 kg bag','Nakuru',CURDATE(),3500,'sample'),
 ('Tomatoes','Crate','Kisumu',CURDATE(),4000,'sample'),
 ('Milk','1 litre','Kisii',CURDATE(),70,'sample'),
 ('Maize','90 kg bag','Kisumu',CURDATE() - INTERVAL 1 DAY,4800,'sample');

INSERT INTO alerts (county_id,type,body_en,body_sw,status,sent_at) VALUES
 (1,'weather','ShambaPulse: Heavy rain expected in Kisii tomorrow. Avoid crossing rivers and stay indoors during lightning.',
  'ShambaPulse: Mvua kubwa inatarajiwa Kisii kesho. Epuka kuvuka mito na ukae ndani wakati wa radi.','sent',NOW()),
 (1,'advisory','ShambaPulse: Short rains starting. Plant certified maize seed and dig drainage channels.',
  'ShambaPulse: Mvua fupi zinaanza. Panda mbegu za mahindi zilizothibitishwa na uchimbe mifereji.','sent',NOW()),
 (2,'market','ShambaPulse: Tomato prices up at Kisumu market this week. Check price before selling.',
  'ShambaPulse: Bei ya nyanya imepanda soko la Kisumu wiki hii. Angalia bei kabla ya kuuza.','sent',NOW());
-- Create an admin:  php web/tools/create_admin.php <username> <password>
