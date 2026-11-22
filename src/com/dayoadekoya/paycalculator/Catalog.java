package com.dayoadekoya.paycalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Known subscriptions and bills for type-ahead suggestions, with typical UK plan prices.
 * Prices checked November 2026 from public UK price comparisons; services whose current
 * UK price couldn't be confirmed are listed without plans, so the app never guesses.
 */
final class Catalog {
    static final String PRICES_AS_OF = "Nov 2026";

    static final class Plan {
        final String label, frequency;
        final double price;
        Plan(String label, double price, String frequency) { this.label = label; this.price = price; this.frequency = frequency; }
    }

    static final class Service {
        final String name, category, frequency, keywords;
        final boolean subscription;
        final List<Plan> plans = new ArrayList<Plan>();
        Service(String name, String category, String frequency, boolean sub, String keywords, String plans) {
            this.name = name; this.category = category; this.frequency = frequency; this.subscription = sub;
            this.keywords = (name + " " + keywords).toLowerCase(Locale.UK);
            if (plans != null && !plans.isEmpty()) {
                for (String p : plans.split(";")) {
                    String[] x = p.split(":");
                    this.plans.add(new Plan(x[0], Double.parseDouble(x[1]), x.length > 2 ? x[2] : frequency));
                }
            }
        }
        /** The main plan's price (the first listed at the usual frequency), for the hint line. */
        double from() {
            for (Plan p : plans) if (p.frequency.equals(frequency)) return p.price;
            return 0;
        }
    }

    // name, category, frequency, subscription, search keywords, plans ("Label:price[:Frequency];…")
    private static final Object[][] RAW = {
            // Streaming
            {"Netflix", "Streaming", "Monthly", true, "", "With ads:7.99;Standard:13.99;Premium 4K:20.99"},
            {"Disney+", "Streaming", "Monthly", true, "disney plus", "With ads:6.99;Standard:11.99;Premium:15.99;Standard yearly:119.90:Yearly;Premium yearly:159.90:Yearly"},
            {"Amazon Prime", "Shopping & memberships", "Monthly", true, "prime video amazon", "Monthly:8.99;Yearly:95:Yearly;Student:4.49;Prime Video only:7.99"},
            {"NOW", "Streaming", "Monthly", true, "now tv sky", "Entertainment:7.99;Ent + HBO Max:9.99;Cinema:9.99;Sports:34.99"},
            {"HBO Max", "Streaming", "Monthly", true, "hbo", "With ads:4.99;Standard:9.99;Premium 4K:14.99"},
            {"Apple TV", "Streaming", "Monthly", true, "apple tv+ appletv", "Monthly:9.99;Yearly:89.99:Yearly"},
            {"Paramount+", "Streaming", "Monthly", true, "paramount plus", "Basic:5.99;Standard:9.99;Premium:12.99"},
            {"ITVX Premium", "Streaming", "Monthly", true, "itv britbox", "Monthly:5.99;Yearly:59.99:Yearly"},
            {"discovery+", "Streaming", "Monthly", true, "discovery plus tnt", "Entertainment:3.99;Premium:6.99;TNT Sports:30.99"},
            {"YouTube Premium", "Streaming", "Monthly", true, "youtube yt", ""},
            {"MUBI", "Streaming", "Monthly", true, "", "Monthly:9.99"},
            {"Crunchyroll", "Streaming", "Monthly", true, "anime", "Fan:4.99;Mega Fan:5.99"},
            {"BFI Player", "Streaming", "Monthly", true, "bfi", "Monthly:6.99"},
            {"Shudder", "Streaming", "Monthly", true, "horror", "Monthly:4.99"},
            {"hayu", "Streaming", "Monthly", true, "reality", "Monthly:4.99"},
            {"DAZN", "Streaming", "Monthly", true, "boxing sport", ""},
            {"TNT Sports", "Streaming", "Monthly", true, "bt sport", ""},
            {"Sky TV", "Streaming", "Monthly", false, "sky", ""},
            // Music & audio
            {"Spotify", "Music", "Monthly", true, "", "Individual:12.99;Duo:17.99;Family:21.99;Student:5.99"},
            {"Apple Music", "Music", "Monthly", true, "itunes", "Individual:10.99;Family:16.99;Student:5.99"},
            {"YouTube Music", "Music", "Monthly", true, "youtube yt", "Individual:10.99;Family:16.99;Student:5.49"},
            {"Amazon Music Unlimited", "Music", "Monthly", true, "amazon music", "Individual:11.99;With Prime:10.99;Family:19.99;Student:5.99"},
            {"Tidal", "Music", "Monthly", true, "", "Individual:10.99;Family:16.99;Student:5.49"},
            {"Deezer", "Music", "Monthly", true, "", "Individual:11.99;Family:19.99;Student:5.99"},
            {"Audible", "Music", "Monthly", true, "audiobooks amazon", ""},
            // Gaming
            {"Xbox Game Pass", "Gaming", "Monthly", true, "xbox gamepass microsoft", "Essential:6.99;Premium:10.99;Ultimate:16.99;PC Game Pass:10.99"},
            {"PlayStation Plus", "Gaming", "Monthly", true, "ps plus psn ps5 sony", "Essential:6.99;Extra:10.99;Premium:13.49;Essential 12 months:59.99:Yearly;Extra 12 months:83.99:Yearly;Premium 12 months:119.99:Yearly"},
            {"Nintendo Switch Online", "Gaming", "Yearly", true, "nintendo switch", ""},
            {"EA Play", "Gaming", "Monthly", true, "ea", ""},
            {"Ubisoft+", "Gaming", "Monthly", true, "ubisoft", ""},
            // Cloud & software
            {"iCloud+", "Software & cloud", "Monthly", true, "icloud apple storage", "50GB:0.99;200GB:2.99;2TB:8.99;6TB:26.99;12TB:54.99"},
            {"Google One", "Software & cloud", "Monthly", true, "google drive storage", "100GB:1.79"},
            {"Microsoft 365", "Software & cloud", "Yearly", true, "office microsoft onedrive", "Personal yearly:84.99:Yearly;Family yearly:104.99:Yearly;Personal monthly:8.49:Monthly;Family monthly:10.49:Monthly;Premium monthly:18.99:Monthly"},
            {"Apple One", "Software & cloud", "Monthly", true, "apple bundle", ""},
            {"Dropbox", "Software & cloud", "Monthly", true, "storage", ""},
            {"Adobe Creative Cloud", "Software & cloud", "Monthly", true, "adobe photoshop lightroom", ""},
            {"Canva Pro", "Software & cloud", "Monthly", true, "canva", ""},
            {"ChatGPT Plus", "Software & cloud", "Monthly", true, "openai chatgpt", ""},
            {"Claude Pro", "Software & cloud", "Monthly", true, "anthropic claude", ""},
            {"1Password", "Software & cloud", "Yearly", true, "password manager", ""},
            {"NordVPN", "Software & cloud", "Yearly", true, "vpn", ""},
            {"Duolingo Super", "Software & cloud", "Yearly", true, "duolingo language", ""},
            // News, reading, wellbeing
            {"Kindle Unlimited", "News & magazines", "Monthly", true, "amazon kindle books", ""},
            {"The Times", "News & magazines", "Monthly", true, "newspaper", ""},
            {"The Telegraph", "News & magazines", "Monthly", true, "newspaper", ""},
            {"The Economist", "News & magazines", "Monthly", true, "magazine", ""},
            {"Headspace", "Fitness", "Yearly", true, "meditation", ""},
            {"Calm", "Fitness", "Yearly", true, "meditation sleep", ""},
            {"Strava", "Fitness", "Yearly", true, "running cycling", ""},
            {"PureGym", "Fitness", "Monthly", true, "gym", ""},
            {"The Gym Group", "Fitness", "Monthly", true, "gym", ""},
            {"David Lloyd", "Fitness", "Monthly", true, "gym", ""},
            {"Gym membership", "Fitness", "Monthly", true, "gym fitness", ""},
            // Memberships & delivery
            {"Deliveroo Plus", "Shopping & memberships", "Monthly", true, "deliveroo food", ""},
            {"Uber One", "Shopping & memberships", "Monthly", true, "uber eats", ""},
            {"Just Eat+", "Shopping & memberships", "Monthly", true, "just eat", ""},
            {"Tesco Clubcard Plus", "Shopping & memberships", "Monthly", true, "tesco", ""},
            {"Costco membership", "Shopping & memberships", "Yearly", true, "costco", ""},
            // Home & bills
            {"Rent", "Rent / Mortgage", "Monthly", false, "landlord", ""},
            {"Mortgage", "Rent / Mortgage", "Monthly", false, "house", ""},
            {"Council tax", "Council tax", "Monthly", false, "council", ""},
            {"TV Licence", "Bills", "Yearly", false, "bbc tv licensing", "2026/27 licence:180:Yearly"},
            {"Energy", "Utilities", "Monthly", false, "gas electricity", ""},
            {"Electricity", "Utilities", "Monthly", false, "power", ""},
            {"Gas", "Utilities", "Monthly", false, "heating", ""},
            {"Water", "Utilities", "Monthly", false, "water rates", ""},
            {"Octopus Energy", "Utilities", "Monthly", false, "octopus energy", ""},
            {"British Gas", "Utilities", "Monthly", false, "energy", ""},
            {"EDF", "Utilities", "Monthly", false, "energy", ""},
            {"E.ON Next", "Utilities", "Monthly", false, "eon energy", ""},
            {"OVO Energy", "Utilities", "Monthly", false, "ovo energy", ""},
            {"Thames Water", "Utilities", "Monthly", false, "water", ""},
            {"Broadband", "Phone & internet", "Monthly", false, "wifi internet fibre", ""},
            {"BT", "Phone & internet", "Monthly", false, "broadband", ""},
            {"Virgin Media", "Phone & internet", "Monthly", false, "broadband", ""},
            {"Sky Broadband", "Phone & internet", "Monthly", false, "sky", ""},
            {"Phone contract", "Phone & internet", "Monthly", false, "mobile sim", ""},
            {"EE", "Phone & internet", "Monthly", false, "mobile", ""},
            {"Vodafone", "Phone & internet", "Monthly", false, "mobile", ""},
            {"O2", "Phone & internet", "Monthly", false, "mobile", ""},
            {"Three", "Phone & internet", "Monthly", false, "mobile", ""},
            {"giffgaff", "Phone & internet", "Monthly", false, "mobile sim", ""},
            {"Car insurance", "Insurance", "Yearly", false, "motor", ""},
            {"Home insurance", "Insurance", "Yearly", false, "contents buildings", ""},
            {"Pet insurance", "Insurance", "Monthly", false, "dog cat", ""},
            {"Life insurance", "Insurance", "Monthly", false, "", ""},
            {"Phone insurance", "Insurance", "Monthly", false, "", ""},
            {"Car finance", "Loans & credit", "Monthly", false, "pcp hp", ""},
            {"Credit card", "Loans & credit", "Monthly", false, "", ""},
            {"Loan", "Loans & credit", "Monthly", false, "personal loan", ""},
            {"Klarna", "Loans & credit", "Monthly", false, "buy now pay later bnpl", ""},
            {"Student loan", "Loans & credit", "Monthly", false, "", ""},
            // Credit cards, buy now pay later and lenders (UK)
            {"American Express", "Loans & credit", "Monthly", false, "amex credit card", ""},
            {"Barclaycard", "Loans & credit", "Monthly", false, "barclays credit card", ""},
            {"Capital One", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Vanquis", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Aqua", "Loans & credit", "Monthly", false, "credit card newday", ""},
            {"Marbles", "Loans & credit", "Monthly", false, "credit card newday", ""},
            {"Fluid", "Loans & credit", "Monthly", false, "credit card newday", ""},
            {"Zable", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Tymit", "Loans & credit", "Monthly", false, "credit card", ""},
            {"HSBC Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Lloyds Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Halifax Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"NatWest Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Santander Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"MBNA", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Tesco Bank Credit Card", "Loans & credit", "Monthly", false, "clubcard credit card", ""},
            {"Sainsbury's Bank Credit Card", "Loans & credit", "Monthly", false, "nectar credit card", ""},
            {"Virgin Money Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"M&S Bank Credit Card", "Loans & credit", "Monthly", false, "marks spencer credit card", ""},
            {"John Lewis Partnership Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Nationwide Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"TSB Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Bank of Scotland Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Post Office Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Asda Money Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Monzo Flex", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Jaja", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Zopa Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"Chase Credit Card", "Loans & credit", "Monthly", false, "credit card", ""},
            {"PayPal Pay in 3", "Loans & credit", "Monthly", false, "bnpl buy now pay later", ""},
            {"PayPal Credit", "Loans & credit", "Monthly", false, "credit", ""},
            {"Clearpay", "Loans & credit", "Monthly", false, "bnpl buy now pay later", ""},
            {"Very", "Loans & credit", "Monthly", false, "catalogue credit account", ""},
            {"Studio", "Loans & credit", "Monthly", false, "catalogue credit", ""},
            {"JD Williams", "Loans & credit", "Monthly", false, "catalogue credit", ""},
            {"Zopa Loan", "Loans & credit", "Monthly", false, "personal loan", ""},
            {"Novuna", "Loans & credit", "Monthly", false, "car finance loan", ""},
            {"Black Horse", "Loans & credit", "Monthly", false, "car finance", ""},
            {"MotoNovo", "Loans & credit", "Monthly", false, "car finance", ""},
            {"Santander Consumer Finance", "Loans & credit", "Monthly", false, "car finance", ""},
            {"Oodle", "Loans & credit", "Monthly", false, "car finance", ""},
            {"Close Brothers", "Loans & credit", "Monthly", false, "car finance", ""},
            {"Lendable", "Loans & credit", "Monthly", false, "loan", ""},
            {"Admiral Loan", "Loans & credit", "Monthly", false, "loan", ""},
            {"Sky Mobile", "Phone & internet", "Monthly", false, "mobile", ""},
            {"Tesco Mobile", "Phone & internet", "Monthly", false, "mobile", ""},
            {"iD Mobile", "Phone & internet", "Monthly", false, "mobile", ""},
            {"Lebara", "Phone & internet", "Monthly", false, "mobile sim", ""},
            {"Smarty", "Phone & internet", "Monthly", false, "mobile sim", ""},
            {"VOXI", "Phone & internet", "Monthly", false, "mobile sim", ""},
            {"TalkTalk", "Phone & internet", "Monthly", false, "broadband", ""},
            {"Plusnet", "Phone & internet", "Monthly", false, "broadband", ""},
            {"Hyperoptic", "Phone & internet", "Monthly", false, "broadband fibre", ""},
            {"Community Fibre", "Phone & internet", "Monthly", false, "broadband fibre", ""},
            {"Utility Warehouse", "Utilities", "Monthly", false, "energy broadband", ""},
            {"Severn Trent", "Utilities", "Monthly", false, "water", ""},
            {"United Utilities", "Utilities", "Monthly", false, "water", ""},
            {"Anglian Water", "Utilities", "Monthly", false, "water", ""},
            {"Yorkshire Water", "Utilities", "Monthly", false, "water", ""},
            {"Southern Water", "Utilities", "Monthly", false, "water", ""},
            {"Welsh Water", "Utilities", "Monthly", false, "water dwr cymru", ""},
            {"Admiral", "Insurance", "Monthly", false, "car insurance", ""},
            {"Aviva", "Insurance", "Monthly", false, "insurance", ""},
            {"Direct Line", "Insurance", "Monthly", false, "insurance", ""},
            {"Churchill", "Insurance", "Monthly", false, "insurance", ""},
            {"LV=", "Insurance", "Monthly", false, "insurance lv", ""},
            {"Hastings Direct", "Insurance", "Monthly", false, "car insurance", ""},
            {"AXA", "Insurance", "Monthly", false, "insurance", ""},
            {"Petplan", "Insurance", "Monthly", false, "pet insurance", ""},
            {"Vitality", "Insurance", "Monthly", false, "health life insurance", ""},
            {"Nuffield Health", "Fitness", "Monthly", true, "gym", ""},
            {"Better Gym", "Fitness", "Monthly", true, "gym leisure", ""},
            {"Anytime Fitness", "Fitness", "Monthly", true, "gym", ""},
            {"Fitness First", "Fitness", "Monthly", true, "gym", ""},
            {"Virgin Active", "Fitness", "Monthly", true, "gym", ""},
            {"Third Space", "Fitness", "Monthly", true, "gym", ""},
            {"Train season ticket", "Transport", "Monthly", false, "rail commute", ""},
            {"Bus pass", "Transport", "Monthly", false, "", ""},
            {"Parking permit", "Transport", "Yearly", false, "", ""},
            {"Road tax", "Transport", "Yearly", false, "vehicle tax ved dvla", ""},
            {"Savings", "Savings", "Monthly", false, "isa", ""},
            {"Childcare", "Bills", "Monthly", false, "nursery", ""},
    };

    static final List<Service> ALL = new ArrayList<Service>();
    static {
        for (Object[] r : RAW) ALL.add(new Service((String) r[0], (String) r[1], (String) r[2], (Boolean) r[3], (String) r[4], (String) r[5]));
    }

    static Service exact(String name) {
        if (name == null) return null;
        String n = name.trim();
        for (Service s : ALL) if (s.name.equalsIgnoreCase(n)) return s;
        return null;
    }

    /** Best matches: names starting with the query first, then any word or keyword match. */
    static List<Service> search(String q, int max) {
        List<Service> out = new ArrayList<Service>();
        String t = q.trim().toLowerCase(Locale.UK);
        if (t.isEmpty()) return out;
        for (Service s : ALL) if (s.name.toLowerCase(Locale.UK).startsWith(t) && out.size() < max) out.add(s);
        for (Service s : ALL) {
            if (out.size() >= max) break;
            if (out.contains(s)) continue;
            if (s.keywords.contains(" " + t) || s.keywords.startsWith(t) || (t.length() >= 3 && s.keywords.contains(t))) out.add(s);
        }
        return out;
    }
}
