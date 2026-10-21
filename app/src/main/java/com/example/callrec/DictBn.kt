package com.example.callrec

/** English labels (used on the Settings-style pages) with their Bangla text. */
object DictBn {
    val map: HashMap<String, String> by lazy {
        val m = HashMap<String, String>(400)
        for (p in pairs()) m[p.first] = p.second
        m
    }

    private fun pairs(): Array<Pair<String, String>> = arrayOf(
        "About" to "অ্যাপ সম্পর্কে",
        "Accent Color" to "অ্যাকসেন্ট রঙ",
        "Amar Khata — ledger, debts, notes and many handy tools in one app." to "আমার খাতা — খরচের খাতা, বাকি, নোট আর আরও অনেক দরকারি টুল এক অ্যাপে।",
        "Animations" to "অ্যানিমেশন",
        "App Icon" to "অ্যাপ আইকন",
        "App Lock" to "অ্যাপ লক",
        "Appearance" to "অ্যাপিয়ারেন্স",
        "Apply" to "প্রয়োগ করুন",
        "Backup & Data" to "ব্যাকআপ ও ডেটা",
        "Backup & Restore" to "ব্যাকআপ ও রিস্টোর",
        "Cache cleared" to "ক্যাশ পরিষ্কার হয়েছে",
        "Cancel" to "বাতিল",
        "Clear Cache" to "ক্যাশ পরিষ্কার",
        "Clear" to "পরিষ্কার করুন",
        "Confirm Before Exit" to "বের হওয়ার আগে নিশ্চিত করুন",
        "Custom colour" to "নিজের রঙ",
        "Dark Mode" to "ডার্ক মোড",
        "Dark" to "ডার্ক",
        "Default Folder" to "ডিফল্ট ফোল্ডার",
        "Delete Data" to "ডেটা মুছুন",
        "Font Size" to "ফন্টের আকার",
        "Guest User" to "অতিথি",
        "Haptic Feedback" to "স্পর্শে কম্পন",
        "Help & Support" to "সাহায্য ও সহায়তা",
        "Hide Tools" to "টুল লুকান",
        "Icon changed. The home screen may take a few seconds to update." to "আইকন বদলেছে। হোম স্ক্রিনে বদলাতে কয়েক সেকেন্ড লাগতে পারে।",
        "Internal Storage" to "অভ্যন্তরীণ স্টোরেজ",
        "Language" to "ভাষা",
        "Light Mode" to "লাইট মোড",
        "Light" to "লাইট",
        "Never" to "কখনো না",
        "Notifications" to "নোটিফিকেশন",
        "OK" to "ঠিক আছে",
        "Off" to "বন্ধ",
        "On" to "চালু",
        "Online sign-in and sync are not available yet. All your data is safely stored on this phone only. Use Backup to keep a copy." to "অনলাইন সাইন-ইন ও সিঙ্ক এখনো পাওয়া যাচ্ছে না। আপনার সব ডেটা শুধু এই ফোনেই নিরাপদে আছে। কপি রাখতে ব্যাকআপ নিন।",
        "Open Backup" to "ব্যাকআপ খুলুন",
        "Other" to "অন্যান্য",
        "Preferences" to "পছন্দসমূহ",
        "Privacy Policy" to "গোপনীয়তা নীতি",
        "Rate Us" to "রেট দিন",
        "Rating opens once the app is on a store" to "অ্যাপ স্টোরে এলে এখান থেকে রেটিং দেওয়া যাবে",
        "Security & Privacy" to "নিরাপত্তা ও গোপনীয়তা",
        "Selected folder" to "বাছা ফোল্ডার",
        "Settings" to "সেটিংস",
        "Share App" to "অ্যাপ শেয়ার করুন",
        "Show Tool Descriptions" to "টুলের বিবরণ দেখান",
        "Show helpful description for each tool" to "প্রতিটি টুলের সহায়ক বিবরণ দেখান",
        "Sign In" to "সাইন ইন",
        "Sign in to sync your data" to "ডেটা সিঙ্ক করতে সাইন ইন করুন",
        "Sound" to "শব্দ",
        "Support & About" to "সহায়তা ও পরিচিতি",
        "System" to "সিস্টেম",
        "Temporary files will be deleted. Your ledger, notes and other data are not touched." to "অস্থায়ী ফাইল মুছে যাবে। আপনার খাতা, নোট ও অন্য ডেটা ছোঁয়া হবে না।",
        "That colour code is not valid" to "রঙের কোডটি ঠিক নয়",
        "Theme Mode" to "থিম মোড",
        "Type a colour code like #2D5BE3. Pick a fairly dark colour so white text stays readable." to "#2D5BE3-এর মতো একটা রঙের কোড লিখুন। একটু গাঢ় রঙ বেছে নিন, যাতে সাদা লেখা পড়া যায়।",
        "Vibration on Action" to "কাজ শেষে কম্পন",
        "" to ""
    )
}
