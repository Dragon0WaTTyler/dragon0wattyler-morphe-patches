import android.os.Parcel;
import android.os.Parcelable;
import dalvik.system.DexClassLoader;
import java.lang.reflect.*;
import java.util.*;

/** Runs local constructor/parcel probes against a private patched APK, without starting Spotify. */
public final class SpotifyArtProbe {
    private static ClassLoader loader;
    private static Class<?> type(String name) throws Exception { return Class.forName(name, true, loader); }
    private static Object read(Object root, String path) throws Exception {
        for (String part : path.split("\\.")) { Field f = root.getClass().getDeclaredField(part); f.setAccessible(true); root = f.get(root); }
        return root;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        loader = new DexClassLoader(args[0], args[1], null, SpotifyArtProbe.class.getClassLoader());
        Class<?> filter = type("app.spicetify.extension.spotify.localserver.nativebridge.ServerLibraryFilter");
        Object own = filter.getConstructor(List.class).newInstance(Collections.emptyList());
        Object course = type("p.vmb0").getConstructor(List.class).newInstance(Collections.emptyList());
        require("spicetify:server".equals(filter.getMethod("getId").invoke(own)), "Server id changed");
        require(!own.equals(course) && !course.equals(own), "Server and Courses collide");
        Parcel parcel = Parcel.obtain();
        ((Parcelable) own).writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Object restored = ((Parcelable.Creator<?>) filter.getField("CREATOR").get(null)).createFromParcel(parcel);
        require(restored.getClass() == filter && own.equals(restored), "Server chip parcel lost identity");
        parcel.recycle();
        Class<?> bindings = type("app.spicetify.extension.spotify.localserver.LibraryRows$Native");
        Constructor<?> constructor = bindings.getDeclaredConstructor(); constructor.setAccessible(true); Object nativeBindings = constructor.newInstance();
        libraryModels(bindings, nativeBindings);
        System.out.println("PASS server native bindings, distinct Courses equality and parcel identity");

        Class<?> bridge = type("app.spicetify.extension.spotify.extensions.nativebridge.MenuBridge");
        Object icon = type("p.tex").getField("c").get(null);
        Object item = bridge.getMethod("item", String[].class, Object.class, int.class, type("p.ugx"))
                .invoke(null, new String[]{"probe", "Probe"}, "spotify:playlist:probe", 2, icon);
        require(type("p.j6m").isInstance(item), "Wrong native menu item");
        Object provider = type("app.spicetify.extension.spotify.extensions.nativebridge.PlaylistMenuProvider")
                .getConstructor().newInstance();
        require(type("p.t6m").isInstance(provider), "Wrong item provider interface");
        System.out.println("PASS menu item/action/analytics constructors and provider interface");

        Class<?> theme = type("app.spicetify.extension.spotify.theme.ComposeTheme");
        Method table = theme.getDeclaredMethod("table"); table.setAccessible(true);
        String[] rawEntries = ((String) table.invoke(null)).split(";", -1)[1].split(",");
        Object raw = type("p.n1x").getField("a").get(null);
        theme.getMethod("primitives", Object.class).invoke(null, raw);
        for (String rawEntry : rawEntries) {
            String[] pair = rawEntry.split("="); String resource = pair[1].split("@")[0];
            Object original = read(raw, pair[0]);
            Map<String,Integer> colors = new HashMap<>(); colors.put(resource, 0xFF125634);
            theme.getMethod("update", Map.class, boolean.class).invoke(null, colors, false);
            require(((Long) read(raw, pair[0])).longValue() == (0xFF125634L << 32), "Native raw color did not change: " + pair[0]);
            theme.getMethod("update", Map.class, boolean.class).invoke(null, Collections.emptyMap(), false);
            require(original.equals(read(raw, pair[0])), "Native raw color did not restore: " + pair[0]);
        }
        System.out.println("PASS actual native raw color copy/update/restore: " + rawEntries.length + " paths");
        settings();
        System.out.println("ART PROBE PASSED (no app startup, login, network or playback test)");
    }
    private static void libraryModels(Class<?> bindings, Object instance) throws Exception {
        Class<?> requestType = type("app.spicetify.extension.spotify.localserver.LibraryRows$Request");
        Constructor<?> request = requestType.getDeclaredConstructor(int.class, int.class, boolean.class, boolean.class, List.class, String.class);
        request.setAccessible(true);
        Object window = request.newInstance(0, 50, true, false, Collections.emptyList(), "probe");
        Class<?> kind = type("app.spicetify.extension.spotify.localserver.LibraryRows$Kind");
        Class<?> entry = type("app.spicetify.extension.spotify.localserver.LibraryRows$Entry");
        Constructor<?> entryConstructor = entry.getDeclaredConstructor(kind, String.class, String.class, String.class, String.class);
        entryConstructor.setAccessible(true);
        Object album = entryConstructor.newInstance(kind.getEnumConstants()[0], "album-probe", "Album", "Artist", "");
        Object artist = entryConstructor.newInstance(kind.getEnumConstants()[1], "artist-probe", "Artist", "", "");
        Method only = bindings.getDeclaredMethod("serverOnly", requestType, List.class); only.setAccessible(true);
        Object page = only.invoke(instance, window, Collections.singletonList(album));
        List<String> updates = Collections.singletonList("update-probe"), decorations = Collections.singletonList("decoration-probe");
        Object decorated = type("p.dec0").getConstructor(type("p.ppd0"), type("p.ddl"), List.class, List.class)
                .newInstance(read(page, "a"), read(page, "b"), updates, decorations);
        Method merge = bindings.getDeclaredMethod("merge", Object.class, requestType, List.class, String.class); merge.setAccessible(true);
        Object merged = merge.invoke(instance, decorated, window, Collections.singletonList(artist), "probe");
        require(read(merged, "c") == updates && read(merged, "d") == decorations, "Library page metadata was lost");
        require(((List<?>) read(merged, "a.c")).size() == 2, "Server album/artist rows did not merge");
        System.out.println("PASS native server album/artist rows, range, page merge and metadata preservation");
    }

    private static void settings() throws Exception {
        if (android.os.Looper.myLooper() == null) android.os.Looper.prepare();
        android.app.Activity activity = new android.app.Activity();
        Object navigator = Proxy.newProxyInstance(loader, new Class<?>[]{type("p.tbn0")},
                (p, method, args) -> method.getReturnType() == boolean.class ? Boolean.FALSE : null);
        java.util.concurrent.atomic.AtomicReference<Object> originalRenderer = new java.util.concurrent.atomic.AtomicReference<>();
        Object lazy = Proxy.newProxyInstance(loader, new Class<?>[]{type("p.l5b0")}, (p, m, args) -> originalRenderer.get());
        Object factory = type("p.vod0").getConstructor(Object.class, Object.class, Object.class, byte.class)
                .newInstance(activity, null, lazy, (byte) 10);
        originalRenderer.set(type("p.fj0").getConstructor(type("p.tbn0"), type("p.vod0")).newInstance(navigator, factory));
        Object provider = type("p.bhn").getConstructor(Object.class, byte.class).newInstance(factory, (byte) 3);
        Object function = Proxy.newProxyInstance(loader, new Class<?>[]{type("p.me30")}, (p, m, args) -> null);
        Object model = type("p.rfo").getConstructor(type("p.me30")).newInstance(function);
        Object text = type("p.tfo").getConstructors()[0].newInstance(null, "Native", null, null, 0);
        Object visible = type("p.dhk1").getField("N0").get(null);
        Object row = type("p.ufo").getConstructor(String.class, type("p.tfo"), type("p.ttf"), type("p.ssx0"), type("p.sfo"))
                .newInstance("native", text, visible, provider, model);
        List<?> fixed = Arrays.asList(row);
        Method append = type("app.spicetify.extension.spotify.settings.nativebridge.SettingsBridge").getMethod("append", List.class);
        List<?> augmented = (List<?>) append.invoke(null, fixed);
        require(fixed.size() == 1 && augmented.size() == 2, "Settings fixed-size list was not copied");
        Object added = augmented.get(1);
        require("spicetify_settings".equals(type("p.ufo").getMethod("getId").invoke(added)), "Settings row id missing");
        List<?> repeated = (List<?>) append.invoke(null, augmented);
        require(repeated.size() == 2, "Settings row duplicated");
        System.out.println("PASS native settings DI/renderer/model construction, fixed-size list copy and idempotence");
    }

}
