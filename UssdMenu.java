package com.shambapulse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Stateless USSD menu (Africa's Talking style: "text" is the whole path, e.g. "4*1*2*1*1").
 * Replies start with "CON " (keep session open) or "END " (close).
 */
public final class UssdMenu {

    private record Farmer(int id, String lang, boolean subscribed, int countyId) {}
    private record Named(int id, String name) {}

    public String handle(String phone, String text) {
        try (Connection c = Db.open()) {
            Farmer f = findFarmer(c, phone);
            String lang = f != null ? f.lang() : "en";
            String[] p = (text == null || text.isEmpty()) ? new String[0] : text.split("\\*", -1);

            if (p.length == 0) return "CON " + mainMenu(lang);

            switch (p[0]) {
                case "1": return "END " + latestAlert(c, "weather", f, lang);
                case "2": return "END " + prices(c, lang);
                case "3": return "END " + latestAlert(c, "advisory", f, lang);
                case "4": return subscribe(c, phone, p);
                case "5": return optOut(c, f, lang);
                default:  return "END " + t(lang, "Invalid choice.", "Chaguo si sahihi.");
            }
        } catch (Exception ex) {
            System.err.println("[ussd] " + ex);
            return "END Service is busy. Please try again. / Huduma imeshughulika, jaribu tena.";
        }
    }

    private static String mainMenu(String lang) {
        return "sw".equals(lang)
                ? "ShambaPulse\n1. Hali ya hewa\n2. Bei za soko\n3. Ushauri\n4. Jisajili kupata tahadhari\n5. Acha kupokea tahadhari"
                : "ShambaPulse\n1. Weather forecast\n2. Market prices\n3. Advisories\n4. Subscribe to alerts\n5. Opt out of alerts";
    }

    private static String t(String lang, String en, String sw) { return "sw".equals(lang) ? sw : en; }

    // ---- options 1-3 ----------------------------------------------------

    private String latestAlert(Connection c, String type, Farmer f, String lang) throws Exception {
        String sql = "SELECT body_en, body_sw FROM alerts WHERE status='sent' AND type=?"
                + (f != null ? " AND county_id=?" : "") + " ORDER BY sent_at DESC LIMIT 1";
        try (PreparedStatement q = c.prepareStatement(sql)) {
            q.setString(1, type);
            if (f != null) q.setInt(2, f.countyId());
            try (ResultSet rs = q.executeQuery()) {
                if (rs.next()) return clip("sw".equals(lang) ? rs.getString(2) : rs.getString(1));
            }
        }
        return t(lang, "No updates yet. Subscribe with option 4 to receive alerts.",
                "Hakuna taarifa bado. Jisajili kwa chaguo 4 kupata tahadhari.");
    }

    private String prices(Connection c, String lang) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement q = c.prepareStatement(
                "SELECT commodity, unit, market, price_kes, DATE_FORMAT(price_date,'%d/%m') d " +
                "FROM prices ORDER BY price_date DESC, id DESC LIMIT 4");
             ResultSet rs = q.executeQuery()) {
            while (rs.next()) {
                sb.append(String.format("%s (%s) %s: KES %,.0f [%s]\n", rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getDouble(4), rs.getString(5)));
            }
        }
        return sb.length() == 0 ? t(lang, "No prices yet.", "Hakuna bei bado.") : sb.toString().trim();
    }

    // ---- option 4: subscribe -------------------------------------------

    private String subscribe(Connection c, String phone, String[] p) throws Exception {
        List<Named> counties = list(c, "SELECT id, name FROM counties ORDER BY id", null);
        if (p.length == 1) return "CON Select county / Chagua kaunti:\n" + numbered(counties);

        Named county = pick(counties, p[1]);
        if (county == null) return "END Invalid choice. / Chaguo si sahihi.";
        List<Named> wards = list(c, "SELECT id, name FROM wards WHERE county_id=? ORDER BY id", county.id());
        if (p.length == 2) return "CON Select ward / Chagua wadi:\n" + numbered(wards);

        Named ward = pick(wards, p[2]);
        if (ward == null) return "END Invalid choice. / Chaguo si sahihi.";
        if (p.length == 3) return "CON Language / Lugha:\n1. English\n2. Kiswahili";

        String lang = "1".equals(p[3]) ? "en" : "2".equals(p[3]) ? "sw" : null;
        if (lang == null) return "END Invalid choice. / Chaguo si sahihi.";
        if (p.length == 4) {
            return "CON " + t(lang,
                    "ShambaPulse will store your phone number, ward and language to send farm alerts (Data Protection Act 2019). Do you agree?\n1. Yes\n2. No",
                    "ShambaPulse itahifadhi namba yako, wadi na lugha ili kukutumia tahadhari (Sheria ya Ulinzi wa Data 2019). Unakubali?\n1. Ndiyo\n2. Hapana");
        }
        if ("1".equals(p[4])) {
            try (PreparedStatement u = c.prepareStatement(
                    "INSERT INTO farmers (phone, ward_id, language, consent_at, subscribed) VALUES (?,?,?,NOW(),1) " +
                    "ON DUPLICATE KEY UPDATE ward_id=VALUES(ward_id), language=VALUES(language), " +
                    "consent_at=NOW(), subscribed=1, opted_out_at=NULL")) {
                u.setString(1, phone); u.setInt(2, ward.id()); u.setString(3, lang);
                u.executeUpdate();
            }
            return "END " + t(lang, "You are subscribed for " + ward.name() + ", " + county.name() + ". Thank you!",
                    "Umejisajili kwa " + ward.name() + ", " + county.name() + ". Asante!");
        }
        // Declined: nothing is stored.
        return "END " + t(lang, "Consent declined. Nothing was saved.", "Hukukubali. Hakuna kilichohifadhiwa.");
    }

    // ---- option 5: opt out ---------------------------------------------

    private String optOut(Connection c, Farmer f, String lang) throws Exception {
        if (f == null || !f.subscribed()) {
            return "END " + t(lang, "You are not subscribed.", "Hujajisajili.");
        }
        try (PreparedStatement u = c.prepareStatement(
                "UPDATE farmers SET subscribed=0, opted_out_at=NOW() WHERE id=?")) {
            u.setInt(1, f.id()); u.executeUpdate();
        }
        return "END " + t(lang, "You will no longer receive alerts.", "Hutapokea tahadhari tena.");
    }

    // ---- helpers ---------------------------------------------------------

    private Farmer findFarmer(Connection c, String phone) throws Exception {
        try (PreparedStatement q = c.prepareStatement(
                "SELECT f.id, f.language, f.subscribed, w.county_id FROM farmers f " +
                "JOIN wards w ON w.id=f.ward_id WHERE f.phone=?")) {
            q.setString(1, phone);
            try (ResultSet rs = q.executeQuery()) {
                return rs.next() ? new Farmer(rs.getInt(1), rs.getString(2), rs.getBoolean(3), rs.getInt(4)) : null;
            }
        }
    }

    private List<Named> list(Connection c, String sql, Integer param) throws Exception {
        List<Named> out = new ArrayList<>();
        try (PreparedStatement q = c.prepareStatement(sql)) {
            if (param != null) q.setInt(1, param);
            try (ResultSet rs = q.executeQuery()) {
                while (rs.next()) out.add(new Named(rs.getInt(1), rs.getString(2)));
            }
        }
        return out;
    }

    private static String numbered(List<Named> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) sb.append(i + 1).append(". ").append(items.get(i).name()).append('\n');
        return sb.toString().trim();
    }

    private static Named pick(List<Named> items, String choice) {
        try {
            int i = Integer.parseInt(choice.trim());
            return (i >= 1 && i <= items.size()) ? items.get(i - 1) : null;
        } catch (NumberFormatException ex) { return null; }
    }

    /** USSD screens are short: keep to ~160 characters. */
    private static String clip(String s) { return s.length() <= 160 ? s : s.substring(0, 157) + "..."; }
}
