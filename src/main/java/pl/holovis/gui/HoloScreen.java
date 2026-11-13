package pl.holovis.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import pl.holovis.Gfx;
import pl.holovis.HoloConfig;
import pl.holovis.HoloSounds;
import pl.holovis.KillEffects;
import pl.holovis.SkyHelper;
import pl.holovis.Spotify;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleToIntFunction;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Menu w stylu kolumn z modułami: panele, przełączniki, rozwijane ustawienia, gradient, własne kolory. */
public class HoloScreen extends Screen {
    private static final int PW = 144, GAP = 8, HEAD = 24, ROW = 20;
    private static final int GRAY = 0x8791A6;
    private static final int CELL = 3, WN = 26;                 // kółko kolorów: 26x26 komórek po 3 px
    private static final int[] WHEEL = buildWheel();
    private static final int[] PRESETS = {0x00F0FF, 0x3D7BFF, 0xB04CFF, 0xFF4FD8, 0xFF3B3B, 0xFF9A1F, 0xFFE81F, 0x3BFF7A, 0xFFFFFF};
    private static final int[] NEUTRALS = {0x000000, 0x0A1020, 0x151515, 0x1E2A3A, 0x3A3A48, 0x808080, 0xC8C8C8, 0xE8EEF8, 0xFFFFFF};

    private static int[] buildWheel() {
        int[] w = new int[WN * WN];
        for (int j = 0; j < WN; j++) {
            for (int i = 0; i < WN; i++) {
                float dx = i + 0.5f - WN / 2f, dy = j + 0.5f - WN / 2f;
                float d = (float) Math.sqrt(dx * dx + dy * dy) / (WN / 2f);
                if (d > 1f) { w[j * WN + i] = -1; continue; }
                float h = (float) (Math.atan2(dy, dx) / (2 * Math.PI));
                if (h < 0) h += 1f;
                w[j * WN + i] = HoloConfig.hsv(h, d, 1f);
            }
        }
        return w;
    }

    private final List<Panel> panels = new ArrayList<>();
    private final long openedAt = Util.getMeasuringTimeMs();
    private float fade = 1f;
    private float sc = 1f;
    private int vw, vh;
    private int accent = 0x00F0FF, accent2 = 0xB04CFF, bgc = 0x0A1020, txtC = 0xE8EEF8;
    private float bgA = 0.88f;
    private boolean grad;
    private String filter = "";
    private boolean searchFocus, placed;
    private Panel dragPanel;
    private int dragDx, dragDy;
    private Setting dragging;
    private long closeStart = -1, lastFrame;
    private static String pendingStatus;
    private String status = "";
    private long statusUntil;

    public HoloScreen() {
        super(Text.literal("KosaVisuals"));
        HoloSounds.refresh();
        build();
        if (pendingStatus != null) { setStatus(pendingStatus); pendingStatus = null; }
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { HoloConfig.save(); }

    // =====================================================================
    //  Budowa modułów
    // =====================================================================
    private void build() {
        HoloConfig c = HoloConfig.get();

        // ---------------- PVP ----------------
        Panel vis = new Panel("PVP");
        Module hb = new Module("Hitbox", () -> c.hitboxEnabled, v -> c.hitboxEnabled = v);
        List<String> styles = Arrays.stream(HoloConfig.HitboxStyle.values()).map(e -> e.label).toList();
        List<String> targets = Arrays.stream(HoloConfig.Target.values()).map(e -> e.label).toList();
        hb.s.add(new DropdownSet("Styl", () -> styles, () -> c.hitboxStyle.ordinal(),
                i -> c.hitboxStyle = HoloConfig.HitboxStyle.values()[i]));
        hb.s.add(new DropdownSet("Cele", () -> targets, () -> c.target.ordinal(),
                i -> c.target = HoloConfig.Target.values()[i]));
        hb.s.add(new SliderSet("Wypełnienie", () -> c.fillAlpha, v -> c.fillAlpha = v, null, v -> (int) (v * 100) + "%"));
        colorSection(hb, "Kolor", c.hitboxColor, PRESETS, null, true);

        Module th = new Module("TargetHUD", () -> c.targetHud, v -> c.targetHud = v);
        th.s.add(new ToggleSet("Serca", () -> c.targetHearts, v -> c.targetHearts = v));
        th.s.add(new ToggleSet("Zbroja", () -> c.targetArmor, v -> c.targetArmor = v));
        colorSection(th, "Kolor", c.tgColor, PRESETS, null, false);
        th.s.add(new ToggleSet("Gradient", () -> c.tgGradient, v -> c.tgGradient = v));
        colorSection(th, "Kolor gradientu", c.tgColor2, PRESETS, null, false);
        th.s.add(new InfoSet("Pokazuje się po najechaniu", "na gracza. Pozycja: klawisz L."));

        List<String> killModes = List.of("Duch", "Piorun", "Duch + piorun");
        Module ke = new Module("Kill Effect", () -> c.killFx, v -> c.killFx = v);
        ke.s.add(new DropdownSet("Tryb", () -> killModes, () -> c.killMode, i -> c.killMode = i));
        ke.s.add(new SliderSet("Czas ducha", () -> (c.killGhostSecs - 1f) / 7f, v -> c.killGhostSecs = 1f + v * 7f, null,
                v -> String.format("%.1f s", 1f + v * 7f)));
        ke.s.add(new SliderSet("Wysokość pioruna", () -> (c.killBoltHeight - 3f) / 37f, v -> c.killBoltHeight = 3f + v * 37f, null,
                v -> (int) (3f + v * 37f) + " bl."));
        ke.s.add(new ToggleSet("Też moby", () -> c.killMobs, v -> c.killMobs = v));
        ke.s.add(new ButtonRow(new String[]{"Test efektu"}, new Runnable[]{KillEffects::spawnTest}));
        colorSection(ke, "Kolor ducha", c.killGhostColor, PRESETS, null, false);
        colorSection(ke, "Kolor pioruna", c.killBoltColor, PRESETS, null, false);

        vis.mods.add(hb);
        vis.mods.add(th);
        vis.mods.add(ke);
        panels.add(vis);

        // ---------------- Dźwięki ----------------
        Panel snd = new Panel("Dźwięki");
        Module hit = new Module("Hit Sound", () -> c.hitSound.enabled, v -> c.hitSound.enabled = v);
        hit.ss = c.hitSound;
        soundRows(hit, c.hitSound);
        Module kill = new Module("Kill Sound", () -> c.killSound.enabled, v -> c.killSound.enabled = v);
        kill.ss = c.killSound;
        soundRows(kill, c.killSound);
        Module click = new Module("Click Sound", () -> c.clickSound.enabled, v -> c.clickSound.enabled = v);
        click.ss = c.clickSound;
        soundRows(click, c.clickSound);
        snd.mods.add(hit);
        snd.mods.add(kill);
        snd.mods.add(click);
        panels.add(snd);

        // ---------------- Świat ----------------
        Panel world = new Panel("Świat");
        Module sky = new Module("Custom Sky", () -> c.skyEnabled, v -> c.skyEnabled = v);
        List<String> skyNames = List.of(SkyHelper.PRESET_NAMES);
        sky.s.add(new DropdownSet("Niebo", () -> skyNames, () -> c.skyPreset, i -> {
            if (i == 0) { c.skyPreset = 0; c.skyEnabled = true; } else SkyHelper.applyPreset(i);
        }));
        Runnable custom = () -> { c.skyPreset = 0; c.skyEnabled = true; };
        colorSection(sky, "Kolor nieba", c.skyColor, PRESETS, custom, false);
        colorSection(sky, "Kolor horyzontu", c.skyColor2, PRESETS, custom, false);
        sky.s.add(new ToggleSet("Pora dnia", () -> c.skyDaylight, v -> c.skyDaylight = v));
        sky.s.add(new ToggleSet("Chmury", () -> c.skyClouds, v -> c.skyClouds = v));
        world.mods.add(sky);
        panels.add(world);

        // ---------------- HUD ----------------
        Panel hud = new Panel("HUD");
        Module wm = new Module("Watermark", () -> c.watermark, v -> c.watermark = v);
        wm.s.add(new ToggleSet("Pokaż FPS", () -> c.showFps, v -> c.showFps = v));
        wm.s.add(new ToggleSet("Pokaż koordynaty", () -> c.coords, v -> c.coords = v));
        colorSection(wm, "Kolor", c.hudColor, PRESETS, null, false);
        wm.s.add(new ToggleSet("Gradient", () -> c.hudGradient, v -> c.hudGradient = v));
        colorSection(wm, "Kolor gradientu", c.hudColor2, PRESETS, null, false);

        Module sp = new Module("Spotify", () -> c.spotify, v -> { c.spotify = v; Spotify.sync(); });
        sp.s.add(new LiveInfoSet(() -> !Spotify.running ? "Nie wykryto Spotify" : (Spotify.playing ? Spotify.track : "Pauza")));
        sp.s.add(new ButtonRow(new String[]{"Poprz.", "Pauza", "Dalej"},
                new Runnable[]{Spotify::previous, Spotify::toggle, Spotify::next}));
        colorSection(sp, "Kolor", c.spColor, PRESETS, null, false);
        sp.s.add(new ToggleSet("Gradient", () -> c.spGradient, v -> c.spGradient = v));
        colorSection(sp, "Kolor gradientu", c.spColor2, PRESETS, null, false);
        sp.s.add(new InfoSet("Czat: najedź na Spotify", "= tekst i przewijanie", "[ ] \\ = skok / pauza"));
        hud.mods.add(wm);
        hud.mods.add(sp);
        panels.add(hud);

        // ---------------- Klient ----------------
        Panel cl = new Panel("Klient");
        Module look = new Module("Akcent GUI", null, null);
        colorSection(look, "Kolor akcentu", c.guiColor, PRESETS, null, false);
        look.s.add(new ToggleSet("Gradient", () -> c.guiGradient, v -> c.guiGradient = v));
        colorSection(look, "Kolor gradientu", c.guiColor2, PRESETS, null, false);
        look.s.add(new ToggleSet("Gradient napisu", () -> c.guiGradientText, v -> c.guiGradientText = v));
        SectionSet bgSec = colorSection(look, "Tło GUI", c.guiBg, NEUTRALS, null, false);
        add(look, bgSec, new SliderSet("Przezroczystość", () -> (c.guiBgAlpha - 0.2f) / 0.8f, v -> c.guiBgAlpha = 0.2f + v * 0.8f,
                null, v -> (int) ((0.2f + v * 0.8f) * 100) + "%"));
        colorSection(look, "Kolor napisu", c.textColor, NEUTRALS, null, false);
        look.s.add(new ToggleSet("Efekt hologramu", () -> c.hologramFx, v -> c.hologramFx = v));
        cl.mods.add(look);
        panels.add(cl);
    }

    private void add(Module m, SectionSet sec, Setting st) {
        st.parent = sec;
        m.s.add(st);
    }

    /** Zwijana sekcja z wyborem koloru: kółko (odcień + nasycenie), paleta, nasycenie, jasność, tęcza. */
    private SectionSet colorSection(Module m, String title, HoloConfig.ColorSetting cs, int[] pal,
                                    Runnable onChange, boolean open) {
        Runnable ch = onChange == null ? () -> {} : onChange;
        SectionSet sec = new SectionSet(m, title, cs);
        sec.open = open;
        m.s.add(sec);
        add(m, sec, new WheelSet(cs, ch));
        add(m, sec, new SwatchSet(cs, pal, ch));
        add(m, sec, new SliderSet("Nasycenie", () -> cs.s, v -> { cs.s = v; ch.run(); },
                f -> HoloConfig.hsv(cs.h, (float) f, Math.max(cs.b, 0.25f)), v -> (int) (v * 100) + "%"));
        add(m, sec, new SliderSet("Jasność", () -> cs.b, v -> { cs.b = v; ch.run(); },
                f -> HoloConfig.hsv(cs.h, cs.s, (float) f), v -> (int) (v * 100) + "%"));
        add(m, sec, new ToggleSet("Tęcza", () -> cs.rainbow, v -> { cs.rainbow = v; ch.run(); }));
        return sec;
    }

    private void soundRows(Module m, HoloConfig.SoundSetting ss) {
        m.s.add(new DropdownSet("Dźwięk",
                () -> HoloSounds.options().stream().map(HoloSounds::label).toList(),
                () -> HoloSounds.options().indexOf(ss.sound),
                i -> { ss.sound = HoloSounds.options().get(i); HoloSounds.play(ss); }));
        m.s.add(new SliderSet("Głośność", () -> ss.volume, v -> ss.volume = v, null, v -> (int) (v * 100) + "%"));
        m.s.add(new SliderSet("Pitch", () -> (ss.pitch - 0.5f) / 1.5f, v -> ss.pitch = 0.5f + v * 1.5f, null,
                v -> String.format("%.2f", 0.5f + v * 1.5f)));
        m.s.add(new ButtonRow(new String[]{"Test", "Folder", "Odśw."},
                new Runnable[]{() -> HoloSounds.play(ss), HoloSounds::openFolder, HoloSounds::refresh}));
        m.s.add(new InfoSet("Przeciągnij plik", ".wav na okno gry,", "aby dodać dźwięk"));
    }

    // =====================================================================
    //  Rysowanie
    // =====================================================================
    private int argb(int rgb, float a) {
        return (((int) (MathHelper.clamp(a * fade, 0f, 1f) * 255)) << 24) | (rgb & 0xFFFFFF);
    }

    private static int mix(int a, int b, float t) { return Gfx.mix(a, b, t); }

    private static int inset(int r, int i) {
        double dy = r - i - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
    }

    /** Zaokrąglony prostokąt z gradientem (pionowym albo poziomym) c1 -> c2 (ARGB). */
    private void box(DrawContext c, int x, int y, int w, int h, int r, int c1, int c2,
                     boolean horiz, boolean top, boolean bottom) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (horiz) {
            for (int i = 0; i < w; i++) {
                int col = Gfx.lerpArgb(c1, c2, w <= 1 ? 0f : i / (float) (w - 1));
                int yt = 0, yb = 0;
                if (r > 0) {
                    int e = i < r ? i : (i >= w - r ? w - 1 - i : -1);
                    if (e >= 0) { int in = inset(r, e); if (top) yt = in; if (bottom) yb = in; }
                }
                c.fill(x + i, y + yt, x + i + 1, y + h - yb, col);
            }
        } else {
            for (int j = 0; j < h; j++) {
                int col = Gfx.lerpArgb(c1, c2, h <= 1 ? 0f : j / (float) (h - 1));
                int in = 0;
                if (r > 0) {
                    if (top && j < r) in = inset(r, j);
                    else if (bottom && j >= h - r) in = inset(r, h - 1 - j);
                }
                c.fill(x + in, y + j, x + w - in, y + j + 1, col);
            }
        }
    }

    private void rrect(DrawContext c, int x, int y, int w, int h, int r, int color, boolean top, boolean bottom) {
        box(c, x, y, w, h, r, color, color, false, top, bottom);
    }

    private void txt(DrawContext c, String s, int x, int y, int rgb, float a) {
        Gfx.text(c, textRenderer, s, x, y, argb(rgb, a), false);
    }

    private void toggle(DrawContext c, int x, int y, int w, int h, boolean on) {
        if (on) box(c, x, y, w, h, h / 2, argb(accent, 1f), argb(grad ? accent2 : accent, 1f), true, true, true);
        else rrect(c, x, y, w, h, h / 2, argb(0x3A4256, 1f), true, true);
        int k = h - 4;
        int kx = on ? x + w - k - 2 : x + 2;
        rrect(c, kx, y + 2, k, k, k / 2, argb(0xFFFFFF, 1f), true, true);
    }

    private boolean matches(Module m) {
        return filter.isEmpty() || m.name.toLowerCase().contains(filter.toLowerCase());
    }

    private boolean shown(Setting st) { return st.parent == null || st.parent.open; }

    private void layout(Panel p, float dt) {
        int cy = p.y + HEAD + 3;
        for (Module m : p.mods) {
            m.visible = matches(m);
            if (!m.visible) { for (Setting st : m.s) st.visible = false; continue; }
            m.rowY = cy;
            cy += ROW;
            int full = 4;
            for (Setting st : m.s) if (shown(st)) full += st.h() + 2;
            float target = m.open ? full : 0f;
            m.animH += (target - m.animH) * Math.min(1f, dt * 14f);
            if (Math.abs(target - m.animH) < 0.6f) m.animH = target;
            int ah = (int) m.animH;
            int sy = cy + 2;
            for (Setting st : m.s) {
                if (!shown(st)) { st.visible = false; continue; }
                st.x = p.x + 8; st.y = sy; st.w = PW - 16;
                st.visible = ah > 0 && st.y + st.h() <= cy + ah - 2;
                sy += st.h() + 2;
            }
            cy += ah;
            m.endY = cy;
        }
        p.h = cy + 3 - p.y;
    }

    @Override
    protected void init() {
        int total = panels.size() * PW + (panels.size() - 1) * GAP;
        sc = MathHelper.clamp((width - 16f) / total, 0.5f, 1f);
        vw = (int) (width / sc);
        vh = (int) (height / sc);
        if (placed) return;
        placed = true;
        int x = Math.max(8, (vw - total) / 2);
        int ord = 0;
        boolean overlap = x + total > vw - 176;   // panele zachodziłyby pod pole Config
        int y0 = overlap ? 74 : 52;
        for (Panel p : panels) { p.x = x; p.y = y0; p.order = ord++; x += PW + GAP; }
    }

    @Override
    public void close() {
        if (closeStart < 0) closeStart = Util.getMeasuringTimeMs();
    }

    @Override
    public void render(DrawContext c, int rawMx, int rawMy, float delta) {
        long now = Util.getMeasuringTimeMs();
        HoloConfig cfg = HoloConfig.get();
        accent = cfg.guiColor.rgb();
        accent2 = cfg.guiColor2.rgb();
        bgc = cfg.guiBg.rgb();
        txtC = cfg.textColor.rgb();
        bgA = cfg.guiBgAlpha;
        grad = cfg.guiGradient;

        float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;

        float closeF = 1f;
        if (closeStart >= 0) {
            float ct = (now - closeStart) / 180f;
            if (ct >= 1f) { if (client != null) client.setScreen(null); return; }
            closeF = 1f - ct;
        }
        float flick = 1f;
        if (cfg.hologramFx) {
            flick = 0.96f + 0.04f * (float) Math.sin(now / 130.0);
            if ((now / 90) % 53 == 0) flick *= 0.85f;
        }
        float intro = Math.min(1f, (now - openedAt) / 260f);
        float introE = 1f - (1f - intro) * (1f - intro) * (1f - intro);

        c.fill(0, 0, width, height, ((int) (0x66 * intro * closeF)) << 24);

        c.getMatrices().push();
        c.getMatrices().scale(sc, sc, 1f);
        int mx = (int) (rawMx / sc), my = (int) (rawMy / sc);

        fade = flick * introE * closeF;

        // tytuł
        c.getMatrices().push();
        c.getMatrices().scale(2f, 2f, 1f);
        int ta = (int) (MathHelper.clamp(fade, 0f, 1f) * 255);
        if (cfg.guiGradientText && grad) Gfx.gradientText(c, textRenderer, "Kosa", 12, 8, accent, accent2, ta, false);
        else Gfx.text(c, textRenderer, "Kosa", 12, 8, (ta << 24) | txtC, false);
        c.getMatrices().pop();
        txt(c, "VISUALS", 24 + textRenderer.getWidth("Kosa") * 2 + 6, 14, accent, 0.9f);

        // wyszukiwarka
        int sx = vw - 170;
        rrect(c, sx, 18, 150, 18, 5, argb(bgc, 0.85f), true, true);
        c.drawBorder(sx, 18, 150, 18, argb(accent, searchFocus ? 0.8f : 0.25f));
        String shown = filter.isEmpty() && !searchFocus ? "Szukaj..." : filter + (searchFocus && (now / 500) % 2 == 0 ? "_" : "");
        txt(c, shown, sx + 8, 24, filter.isEmpty() ? GRAY : txtC, 1f);

        // Config pod wyszukiwarką
        rrect(c, sx, 42, 150, 22, 6, argb(bgc, 0.85f), true, true);
        c.drawBorder(sx, 42, 150, 22, argb(accent, 0.25f));
        for (int i = 0; i < 2; i++) {
            String lab = i == 0 ? "Kopiuj kod" : "Load config";
            int bx = sx + 3 + i * 74;
            boolean hov = mx >= bx && mx < bx + 70 && my >= 45 && my < 61;
            rrect(c, bx, 45, 70, 16, 4, argb(accent, hov ? 0.42f : 0.22f), true, true);
            txt(c, lab, bx + (70 - textRenderer.getWidth(lab)) / 2, 49, txtC, 1f);
        }

        if (cfg.hologramFx) {
            int sy = (int) ((now / 10) % (vh + 40)) - 20;
            c.fillGradient(0, sy - 14, vw, sy, argb(accent, 0f), argb(accent, 0.07f));
            c.fill(0, sy, vw, sy + 1, argb(accent, 0.12f));
        }

        // panele: wjeżdżają kolejno (stagger), znikają przy zamykaniu
        for (Panel p : panels) {
            float pt = MathHelper.clamp((now - openedAt - p.order * 70L) / 320f, 0f, 1f);
            float e = 1f - (1f - pt) * (1f - pt) * (1f - pt);
            fade = flick * e * closeF;
            c.getMatrices().push();
            c.getMatrices().translate(0f, (1f - e) * -20f + (1f - closeF) * -12f, 0f);
            renderPanel(c, p, mx, my, cfg.hologramFx, dt);
            c.getMatrices().pop();
        }

        fade = flick * closeF;
        if (now < statusUntil) {
            int w = textRenderer.getWidth(status) + 20;
            int x = (vw - w) / 2, y = vh - 34;
            rrect(c, x, y, w, 20, 6, argb(bgc, 0.92f), true, true);
            txt(c, status, x + 10, y + 6, txtC, 1f);
        }
        txt(c, "Prawy Shift / ESC - zamknij  |  L - edytor HUD  |  PPM - ustawienia", 10, vh - 14, txtC, 0.6f);

        c.getMatrices().pop();
    }

    private void renderPanel(DrawContext c, Panel p, int mx, int my, boolean fx, float dt) {
        layout(p, dt);
        int bodyTop = argb(grad ? mix(bgc, accent, 0.30f) : bgc, bgA);
        int bodyBot = argb(grad ? mix(bgc, accent2, 0.30f) : bgc, bgA);
        box(c, p.x, p.y + HEAD, PW, p.h - HEAD, 6, bodyTop, bodyBot, false, false, true);
        if (grad) {
            box(c, p.x, p.y, PW, HEAD, 6, argb(mix(bgc, accent, 0.8f), 0.95f), argb(mix(bgc, accent2, 0.8f), 0.95f), true, true, false);
        } else {
            rrect(c, p.x, p.y, PW, HEAD, 6, argb(mix(bgc, accent, 0.24f), 0.94f), true, false);
        }
        if (fx) {
            for (int yy = p.y + HEAD; yy < p.y + p.h - 3; yy += 3)
                c.fill(p.x + 3, yy, p.x + PW - 3, yy + 1, argb(accent, 0.04f));
        }

        txt(c, p.title, p.x + 10, p.y + 8, txtC, 1f);
        int vis = 0;
        for (Module m : p.mods) if (m.visible) vis++;
        String cnt = String.valueOf(vis);
        txt(c, cnt, p.x + PW - 10 - textRenderer.getWidth(cnt), p.y + 8, txtC, 0.7f);

        for (Module m : p.mods) {
            if (!m.visible) continue;
            boolean hov = mx >= p.x && mx < p.x + PW && my >= m.rowY && my < m.rowY + ROW;
            boolean hasToggle = m.on != null;
            boolean on = !hasToggle || m.on.getAsBoolean();
            if (hov) c.fill(p.x + 4, m.rowY, p.x + PW - 4, m.rowY + ROW, argb(accent, 0.12f));
            if (hasToggle && on) {
                if (grad) c.fillGradient(p.x + 4, m.rowY + 4, p.x + 6, m.rowY + ROW - 4, argb(accent, 1f), argb(accent2, 1f));
                else c.fill(p.x + 4, m.rowY + 4, p.x + 6, m.rowY + ROW - 4, argb(accent, 1f));
            }
            txt(c, m.name, p.x + 12, m.rowY + 6, txtC, on ? 1f : 0.5f);
            if (!m.s.isEmpty()) txt(c, m.open ? "v" : ">", p.x + PW - 46, m.rowY + 6, txtC, 0.6f);
            if (hasToggle) toggle(c, p.x + PW - 32, m.rowY + 5, 22, 10, on);

            if (m.animH >= 1f) {
                c.fill(p.x + 4, m.rowY + ROW, p.x + PW - 4, m.endY, argb(0x000000, 0.28f));
                for (Setting st : m.s) if (st.visible) st.draw(c, mx, my);
            }
        }
    }

    // =====================================================================
    //  Input
    // =====================================================================
    private void toggleOpen(Panel p, Module m) {
        boolean to = !m.open;
        for (Module o : p.mods) o.open = false;
        m.open = to;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        mx /= sc;
        my /= sc;
        int sx = vw - 170;
        searchFocus = mx >= sx && mx < sx + 150 && my >= 18 && my < 36;
        if (searchFocus) return true;
        if (mx >= sx && mx < sx + 150 && my >= 42 && my < 64) {
            if (button == 0) { if (mx < sx + 75) copyConfig(); else loadConfig(); }
            return true;
        }

        for (int i = panels.size() - 1; i >= 0; i--) {
            Panel p = panels.get(i);
            if (mx < p.x || mx >= p.x + PW || my < p.y || my >= p.y + p.h) continue;
            if (my < p.y + HEAD) {
                if (button == 0) {
                    dragPanel = p;
                    dragDx = (int) mx - p.x;
                    dragDy = (int) my - p.y;
                    panels.remove(i);
                    panels.add(p);
                }
                return true;
            }
            for (Module m : p.mods) {
                if (!m.visible) continue;
                if (my >= m.rowY && my < m.rowY + ROW) { rowClick(p, m, mx, button); return true; }
                if (m.open) {
                    for (Setting s : m.s) {
                        if (s.visible && s.hit(mx, my)) {
                            s.click(mx, my, button);
                            if (s.draggable()) dragging = s;
                            return true;
                        }
                    }
                }
            }
            return true;
        }
        return true;
    }

    private void rowClick(Panel p, Module m, double mx, int button) {
        boolean hasToggle = m.on != null, hasSet = !m.s.isEmpty();
        if (button == 1) { if (hasSet) toggleOpen(p, m); return; }
        if (button != 0) return;
        int rx = (int) mx - p.x;
        if (hasToggle && rx >= PW - 36) m.setOn.accept(!m.on.getAsBoolean());
        else if (hasSet && (rx >= PW - 54 || !hasToggle)) toggleOpen(p, m);
        else if (hasToggle) m.setOn.accept(!m.on.getAsBoolean());
        HoloConfig.save();
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        mx /= sc;
        my /= sc;
        if (dragPanel != null) {
            dragPanel.x = MathHelper.clamp((int) mx - dragDx, 0, Math.max(0, vw - PW));
            dragPanel.y = MathHelper.clamp((int) my - dragDy, 0, Math.max(0, vh - HEAD));
            return true;
        }
        if (dragging != null) { dragging.drag(mx, my); return true; }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        mx /= sc;
        my /= sc;
        for (int i = panels.size() - 1; i >= 0; i--) {
            for (Module m : panels.get(i).mods) {
                if (!m.visible || !m.open) continue;
                for (Setting st : m.s) if (st.visible && st.onScroll(mx, my, vAmt)) return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (dragging != null || dragPanel != null) HoloConfig.save();
        dragging = null;
        dragPanel = null;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (searchFocus && chr >= 32 && filter.length() < 20) { filter += chr; return true; }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searchFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !filter.isEmpty()) { filter = filter.substring(0, filter.length() - 1); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) { searchFocus = false; return true; }
            return true;
        }
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        if (key == GLFW.GLFW_KEY_L) { client.setScreen(new HudEditScreen(this)); return true; }
        return super.keyPressed(key, scan, mods);
    }

    /** Przeciągnięcie plików .wav na okno gry = dodanie własnego dźwięku. */
    public void onFilesDropped(List<Path> paths) {
        String last = null;
        int ok = 0;
        for (Path p : paths) {
            String k = HoloSounds.importFile(p);
            if (k != null) { last = k; ok++; }
        }
        if (ok == 0) { setStatus("Obsługiwane są tylko pliki .wav"); return; }
        Module target = null, fallback = null;
        for (Panel p : panels) for (Module m : p.mods) {
            if (m.ss == null) continue;
            if (fallback == null) fallback = m;
            if (m.open) target = m;
        }
        if (target == null) target = fallback;
        if (target != null) { target.ss.sound = last; HoloConfig.save(); }
        setStatus("Dodano dźwięk: " + HoloSounds.label(last));
    }

    private void copyConfig() {
        String code = HoloConfig.exportCode();
        if (code.isEmpty()) { setStatus("Nie udało się utworzyć kodu"); return; }
        client.keyboard.setClipboard(code);
        setStatus("Skopiowano kod configu do schowka");
    }

    private void loadConfig() {
        String err = HoloConfig.importCode(client.keyboard.getClipboard());
        if (err != null) { setStatus(err); return; }
        Spotify.sync();
        HoloSounds.refresh();
        pendingStatus = "Wczytano config";
        client.setScreen(new HoloScreen());   // odbuduj menu na nowych ustawieniach
    }

    private void setStatus(String s) {
        status = s;
        statusUntil = Util.getMeasuringTimeMs() + 3500;
    }

    // =====================================================================
    //  Struktury
    // =====================================================================
    private class Panel {
        final String title;
        final List<Module> mods = new ArrayList<>();
        int x, y, h, order;
        Panel(String t) { title = t; }
    }

    private class Module {
        final String name;
        final BooleanSupplier on;
        final Consumer<Boolean> setOn;
        final List<Setting> s = new ArrayList<>();
        HoloConfig.SoundSetting ss;
        boolean open, visible = true;
        float animH;
        int rowY, endY;
        Module(String n, BooleanSupplier on, Consumer<Boolean> setOn) { name = n; this.on = on; this.setOn = setOn; }
    }

    private abstract class Setting {
        int x, y, w;
        boolean visible = true;
        SectionSet parent;
        boolean onScroll(double mx, double my, double amount) { return false; }
        abstract int h();
        abstract void draw(DrawContext c, int mx, int my);
        boolean click(double mx, double my, int button) { return false; }
        void drag(double mx, double my) {}
        boolean draggable() { return false; }
        boolean hit(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h(); }
        boolean hover(int mx, int my) { return hit(mx, my); }
    }

    /** Tekst etykiety: kolor napisu z lekką przezroczystością (czytelny na każdym tle). */
    private void mtxt(DrawContext c, String s, int x, int y) { txt(c, s, x, y, txtC, 0.72f); }

    private int accentLight() { return mix(accent, 0xFFFFFF, 0.55f); }

    private class SectionSet extends Setting {
        final Module mod; final String label; final HoloConfig.ColorSetting cs;
        boolean open;
        SectionSet(Module m, String l, HoloConfig.ColorSetting cs) { mod = m; label = l; this.cs = cs; }
        int h() { return 17; }
        void draw(DrawContext c, int mx, int my) {
            rrect(c, x - 2, y, w + 4, 16, 4, argb(accent, hover(mx, my) ? 0.24f : 0.13f), true, true);
            txt(c, label, x + 4, y + 4, txtC, 0.95f);
            if (cs != null) {
                Gfx.circle(c, x + w - 24, y + 8, 5, argb(0xFFFFFF, 0.85f));
                Gfx.circle(c, x + w - 24, y + 8, 4, argb(cs.rgb(), 1f));
            }
            txt(c, open ? "v" : ">", x + w - 9, y + 4, txtC, 0.7f);
        }
        boolean click(double mx, double my, int b) {
            boolean to = !open;
            for (Setting o : mod.s) if (o instanceof SectionSet ss) ss.open = false;
            open = to;
            return true;
        }
    }

    private class ToggleSet extends Setting {
        final String label; final BooleanSupplier get; final Consumer<Boolean> set;
        ToggleSet(String l, BooleanSupplier g, Consumer<Boolean> s) { label = l; get = g; set = s; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            if (hover(mx, my)) c.fill(x - 2, y, x + w + 2, y + 16, argb(accent, 0.10f));
            txt(c, label, x + 2, y + 4, txtC, 0.95f);
            toggle(c, x + w - 20, y + 4, 18, 8, get.getAsBoolean());
        }
        boolean click(double mx, double my, int b) { set.accept(!get.getAsBoolean()); HoloConfig.save(); return true; }
    }

    private class SliderSet extends Setting {
        final String label; final Supplier<Float> get; final Consumer<Float> set;
        final DoubleToIntFunction track; final Function<Float, String> fmt;
        SliderSet(String l, Supplier<Float> g, Consumer<Float> s, DoubleToIntFunction t, Function<Float, String> f) {
            label = l; get = g; set = s; track = t; fmt = f;
        }
        int h() { return 25; }
        boolean draggable() { return true; }
        void draw(DrawContext c, int mx, int my) {
            float v = MathHelper.clamp(get.get(), 0f, 1f);
            mtxt(c, label, x + 2, y + 1);
            String s = fmt.apply(v);
            txt(c, s, x + w - textRenderer.getWidth(s) - 2, y + 1, txtC, 1f);
            int ty = y + 14, tw = w - 4, tx = x + 2, th = 6;
            int knobCol;
            if (track != null) {
                Gfx.rboxH(c, tx, ty, tw, th, 3, argb(track.applyAsInt(0), 1f), argb(track.applyAsInt(1), 1f));
                knobCol = track.applyAsInt(v);
            } else {
                rrect(c, tx, ty, tw, th, 3, argb(0x2A3246, 1f), true, true);
                int fw = (int) (tw * v);
                if (fw > 0) {
                    int w2 = Math.max(th, fw);
                    Gfx.rboxH(c, tx, ty, w2, th, 3, argb(accent, 1f), argb(grad ? accent2 : accent, 1f));
                }
                knobCol = accent;
            }
            int kx = tx + (int) (v * tw), ky = ty + th / 2;
            Gfx.circle(c, kx, ky, 6, argb(0x000000, 0.35f));
            Gfx.circle(c, kx, ky, 5, argb(0xFFFFFF, 1f));
            Gfx.circle(c, kx, ky, 3, argb(knobCol, 1f));
        }
        boolean click(double mx, double my, int b) { drag(mx, my); return true; }
        void drag(double mx, double my) { set.accept(MathHelper.clamp((float) ((mx - (x + 2)) / (w - 4)), 0f, 1f)); }
    }

    /** Kółko kolorów: kąt = odcień, odległość od środka = nasycenie. */
    private class WheelSet extends Setting {
        final HoloConfig.ColorSetting cs; final Runnable ch;
        WheelSet(HoloConfig.ColorSetting cs, Runnable ch) { this.cs = cs; this.ch = ch; }
        int h() { return WN * CELL + 6; }
        boolean draggable() { return true; }
        int cx() { return x + w / 2; }
        int cy() { return y + 3 + WN * CELL / 2; }
        boolean hit(double mx, double my) {
            double dx = mx - cx(), dy = my - cy(), r = WN * CELL / 2.0 + 4;
            return dx * dx + dy * dy <= r * r;
        }
        void draw(DrawContext c, int mx, int my) {
            int R = WN * CELL / 2;
            Gfx.circle(c, cx(), cy(), R + 2, argb(0x000000, 0.5f));
            int wx = cx() - R, wy = cy() - R;
            float dimK = 0.3f + 0.7f * cs.b;
            for (int j = 0; j < WN; j++) {
                for (int i = 0; i < WN; i++) {
                    int rgb = WHEEL[j * WN + i];
                    if (rgb < 0) continue;
                    int x0 = wx + i * CELL, y0 = wy + j * CELL;
                    c.fill(x0, y0, x0 + CELL, y0 + CELL, argb(mix(0x000000, rgb, dimK), 1f));
                }
            }
            double ang = cs.h * 2 * Math.PI;
            double rad = cs.s * (R - 2);
            int mkx = cx() + (int) Math.round(Math.cos(ang) * rad), mky = cy() + (int) Math.round(Math.sin(ang) * rad);
            Gfx.circle(c, mkx, mky, 6, argb(0x000000, 0.55f));
            Gfx.circle(c, mkx, mky, 5, argb(0xFFFFFF, 1f));
            Gfx.circle(c, mkx, mky, 3, argb(HoloConfig.hsv(cs.h, cs.s, cs.b), 1f));
            // podgląd wybranego koloru
            Gfx.circle(c, x + 10, y + 11, 9, argb(0xFFFFFF, 0.85f));
            Gfx.circle(c, x + 10, y + 11, 8, argb(cs.rgb(), 1f));
        }
        void pick(double mx, double my) {
            double dx = mx - cx(), dy = my - cy();
            double R = WN * CELL / 2.0 - 2;
            double d = Math.sqrt(dx * dx + dy * dy);
            double a = Math.atan2(dy, dx) / (2 * Math.PI);
            if (a < 0) a += 1;
            cs.h = (float) a;
            cs.s = (float) Math.min(1.0, d / R);
            cs.rainbow = false;
            ch.run();
        }
        boolean click(double mx, double my, int b) { pick(mx, my); return true; }
        void drag(double mx, double my) { pick(mx, my); }
    }

    private class SwatchSet extends Setting {
        final HoloConfig.ColorSetting cs; final int[] pal; final Runnable ch;
        SwatchSet(HoloConfig.ColorSetting cs, int[] pal, Runnable ch) { this.cs = cs; this.pal = pal; this.ch = ch; }
        int h() { return 16; }
        void draw(DrawContext c, int mx, int my) {
            for (int i = 0; i < pal.length; i++) {
                int cx = x + 7 + i * 14, cy = y + 8;
                boolean hov = (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= 36;
                if (hov) Gfx.circle(c, cx, cy, 7, argb(0xFFFFFF, 0.95f));
                Gfx.circle(c, cx, cy, 6, argb(0x000000, 0.45f));
                Gfx.circle(c, cx, cy, 5, argb(pal[i], 1f));
            }
        }
        boolean click(double mx, double my, int b) {
            int i = (int) Math.round((mx - (x + 7)) / 14.0);
            if (i >= 0 && i < pal.length) {
                double cx = x + 7 + i * 14, cy = y + 8;
                if ((mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= 49) {
                    cs.set(pal[i]);
                    cs.rainbow = false;
                    ch.run();
                    HoloConfig.save();
                }
            }
            return true;
        }
    }

    private class ButtonRow extends Setting {
        final String[] labels; final Runnable[] actions;
        ButtonRow(String[] l, Runnable[] a) { labels = l; actions = a; }
        int h() { return 18; }
        int bw() { return (w - 4 * (labels.length - 1)) / labels.length; }
        void draw(DrawContext c, int mx, int my) {
            int bw = bw();
            for (int i = 0; i < labels.length; i++) {
                int bx = x + i * (bw + 4);
                boolean hov = mx >= bx && mx < bx + bw && my >= y && my < y + 16;
                rrect(c, bx, y, bw, 16, 4, argb(accent, hov ? 0.40f : 0.22f), true, true);
                int tw = textRenderer.getWidth(labels[i]);
                txt(c, labels[i], bx + (bw - tw) / 2, y + 4, txtC, 1f);
            }
        }
        boolean click(double mx, double my, int b) {
            int bw = bw();
            int i = (int) ((mx - x) / (bw + 4));
            if (i >= 0 && i < actions.length && (mx - x) - i * (bw + 4) < bw) actions[i].run();
            return true;
        }
    }

    private class DropdownSet extends Setting {
        static final int IH = 13, MAXV = 6;
        final String label; final Supplier<List<String>> opts; final IntSupplier get; final IntConsumer set;
        boolean open;
        int scroll;
        DropdownSet(String l, Supplier<List<String>> o, IntSupplier g, IntConsumer s) { label = l; opts = o; get = g; set = s; }
        int rows() { return Math.min(MAXV, opts.get().size()); }
        int h() { return 16 + (open ? rows() * IH + 4 : 0); }
        void draw(DrawContext c, int mx, int my) {
            boolean hovHead = mx >= x && mx < x + w && my >= y && my < y + 16;
            if (hovHead) c.fill(x - 2, y, x + w + 2, y + 16, argb(accent, 0.10f));
            mtxt(c, label, x + 2, y + 4);
            List<String> o = opts.get();
            int sel = get.getAsInt();
            String cur = sel >= 0 && sel < o.size() ? o.get(sel) : "?";
            String arrow = open ? "^" : "v";
            int maxW = w - textRenderer.getWidth(label) - 22;
            cur = textRenderer.trimToWidth(cur, Math.max(20, maxW));
            String right = cur + " " + arrow;
            txt(c, right, x + w - textRenderer.getWidth(right) - 2, y + 4, accentLight(), 1f);
            if (!open) return;
            int n = o.size(), r = rows();
            scroll = MathHelper.clamp(scroll, 0, Math.max(0, n - r));
            int top = y + 17;
            rrect(c, x - 2, top - 1, w + 4, r * IH + 4, 3, argb(0x000000, 0.45f), true, true);
            for (int i = 0; i < r; i++) {
                int idx = scroll + i;
                int iy = top + i * IH;
                boolean hov = mx >= x && mx < x + w - 6 && my >= iy && my < iy + IH;
                if (hov) rrect(c, x, iy, w - 6, IH, 3, argb(accent, 0.28f), true, true);
                String t = textRenderer.trimToWidth(o.get(idx), w - 18);
                txt(c, t, x + 5, iy + 3, idx == sel ? accentLight() : txtC, 1f);
            }
            if (n > r) {
                int trackH = r * IH;
                int knobH = Math.max(8, trackH * r / n);
                int knobY = top + (trackH - knobH) * scroll / Math.max(1, n - r);
                c.fill(x + w - 3, top, x + w - 1, top + trackH, argb(0xFFFFFF, 0.08f));
                c.fill(x + w - 3, knobY, x + w - 1, knobY + knobH, argb(accent, 0.8f));
            }
        }
        boolean click(double mx, double my, int b) {
            if (my < y + 16) { open = !open; return true; }
            if (!open) return true;
            List<String> o = opts.get();
            int idx = scroll + (int) ((my - (y + 17)) / IH);
            if (my >= y + 17 && idx >= 0 && idx < o.size() && idx < scroll + rows()) {
                set.accept(idx);
                open = false;
                HoloConfig.save();
            }
            return true;
        }
        boolean onScroll(double mx, double my, double amount) {
            if (!open || !hit(mx, my)) return false;
            scroll -= (int) Math.signum(amount);
            return true;
        }
    }

    private class LiveInfoSet extends Setting {
        final Supplier<String> text; final String prefix;
        LiveInfoSet(Supplier<String> t) { this(t, "Teraz"); }
        LiveInfoSet(Supplier<String> t, String prefix) { text = t; this.prefix = prefix; }
        int h() { return 12; }
        void draw(DrawContext c, int mx, int my) {
            String t = textRenderer.trimToWidth(prefix + ": " + text.get(), w - 4);
            txt(c, t, x + 2, y + 2, accentLight(), 1f);
        }
    }

    private class InfoSet extends Setting {
        final String[] lines;
        InfoSet(String... l) { lines = l; }
        int h() { return lines.length * 10 + 2; }
        void draw(DrawContext c, int mx, int my) {
            for (int i = 0; i < lines.length; i++) txt(c, lines[i], x + 2, y + 2 + i * 10, txtC, 0.6f);
        }
    }
}
