package app.pixelbureau.logic

/** Conference-room "TEAMWORK" poster: pixel rows (one char per pixel) + palette. Shared with the Pillow mock. */
object PosterArt {
    val rows = listOf(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
        "FRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRF",
        "FRRWWWRWWWRRWRRWRWRWRWRWWWRWWRRWRWRF",
        "FRRRWRRWRRRWRWRWWWRWRWRWRWRWRWRWRWRF",
        "FRRRWRRWWRRWWWRWWWRWWWRWRWRWWRRWWRRF",
        "FRRRWRRWRRRWRWRWRWRWWWRWRWRWRWRWRWRF",
        "FRRRWRRWWWRWRWRWRWRWRWRWWWRWRWRWRWRF",
        "FrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrF",
        "F................yy................F",
        "F..............y....y..............F",
        "F................ss................F",
        "F..........hhhy..bg..yooo..........F",
        "F..........hss..b..g..sso..........F",
        "F..........sks.b....g.sks..........F",
        "F..........sssb......gsss..........F",
        "F.........bbbb........gggg.........F",
        "F.........bbbb........gggg.........F",
        "F.........bbbb........gggg.........F",
        "F.........sbbb........gggs.........F",
        "F..........n.n........n.n..........F",
        "F..........n.n........n.n..........F",
        "F..........n.n........n.n..........F",
        "FLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLF",
        "FGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGF",
        "FGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGGF",
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF",
    )

    val palette: Map<Char, Long> = mapOf(
        '.' to 0xFFFFF6E0,
        'F' to 0xFF7A4E2C,
        'R' to 0xFFD64A3A,
        'r' to 0xFFA6342A,
        'W' to 0xFFFFFFFF,
        's' to 0xFFF7C8A0,
        'h' to 0xFF3C281E,
        'o' to 0xFFDE8C32,
        'k' to 0xFF281E1E,
        'b' to 0xFF4682DC,
        'g' to 0xFF3CAA64,
        'n' to 0xFF28325A,
        'y' to 0xFFFFCD3C,
        'G' to 0xFF78BE5A,
        'L' to 0xFF5A9646,
    )
}
