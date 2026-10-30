package com.shambapulse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends alerts the officer queued in the PHP dashboard.
 * Rules from the site: only consented + subscribed farmers in the alert's county;
 * no eligible farmer -> alert goes back to draft (never marked sent);
 * "submitted" is recorded separately from "delivered".
 */
public final class Dispatcher {
    private final SmsGateway sms;
    private final ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();

    public Dispatcher(SmsGateway sms) { this.sms = sms; }

    public void start(int everySeconds) {
        pool.scheduleWithFixedDelay(() -> {
            try { runOnce(); } catch (Exception ex) { System.err.println("[dispatcher] " + ex); }
        }, 3, everySeconds, TimeUnit.SECONDS);
    }

    public void stop() { pool.shutdownNow(); }

    record Farmer(int id, String phone, String lang) {}

    void runOnce() throws Exception {
        try (Connection c = Db.open();
             PreparedStatement q = c.prepareStatement(
                     "SELECT id, county_id, body_en, body_sw FROM alerts WHERE status='queued' ORDER BY id");
             ResultSet rs = q.executeQuery()) {
            List<Object[]> queued = new ArrayList<>();
            while (rs.next()) {
                queued.add(new Object[]{rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4)});
            }
            for (Object[] a : queued) dispatch(c, (int) a[0], (int) a[1], (String) a[2], (String) a[3]);
        }
    }

    private void dispatch(Connection c, int alertId, int countyId, String en, String sw) throws Exception {
        List<Farmer> farmers = new ArrayList<>();
        try (PreparedStatement q = c.prepareStatement(
                "SELECT f.id, f.phone, f.language FROM farmers f JOIN wards w ON w.id=f.ward_id " +
                "WHERE w.county_id=? AND f.subscribed=1 AND f.consent_at IS NOT NULL")) {
            q.setInt(1, countyId);
            try (ResultSet rs = q.executeQuery()) {
                while (rs.next()) farmers.add(new Farmer(rs.getInt(1), rs.getString(2), rs.getString(3)));
            }
        }
        if (farmers.isEmpty()) {
            try (PreparedStatement u = c.prepareStatement("UPDATE alerts SET status='draft' WHERE id=?")) {
                u.setInt(1, alertId); u.executeUpdate();
            }
            System.out.println("[dispatcher] alert " + alertId + ": no eligible farmers, kept as draft");
            return;
        }
        try (PreparedStatement ins = c.prepareStatement(
                "INSERT IGNORE INTO alert_deliveries (alert_id, farmer_id, status, provider_ref) VALUES (?,?,?,?)")) {
            for (Farmer f : farmers) {
                SmsGateway.Result r = sms.send(f.phone(), "sw".equals(f.lang()) ? sw : en);
                ins.setInt(1, alertId);
                ins.setInt(2, f.id());
                ins.setString(3, r.ok() ? "submitted" : "failed");
                ins.setString(4, r.providerRef());
                ins.executeUpdate();
            }
        }
        try (PreparedStatement u = c.prepareStatement("UPDATE alerts SET status='sent', sent_at=NOW() WHERE id=?")) {
            u.setInt(1, alertId); u.executeUpdate();
        }
        System.out.println("[dispatcher] alert " + alertId + " submitted to " + farmers.size() + " farmer(s)");
    }

    /** Called by the delivery-report webhook. */
    public static void recordDelivery(String providerRef, String providerStatus) throws Exception {
        String status = "Success".equalsIgnoreCase(providerStatus) || "Delivered".equalsIgnoreCase(providerStatus)
                ? "delivered" : "failed";
        try (Connection c = Db.open();
             PreparedStatement u = c.prepareStatement("UPDATE alert_deliveries SET status=? WHERE provider_ref=?")) {
            u.setString(1, status);
            u.setString(2, providerRef);
            u.executeUpdate();
        }
    }
}
