package pt.ua.bioinformatics.diseasecard.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import pt.ua.bioinformatics.diseasecard.common.Config;

/**
 * Diseasecard-specific configuration properties loader.
 *
 * @author pedrolopes
 */
public class DC4 {

    private static boolean loaded = false;
    private static String solrHost = "";
    private static String indexString = "";
    private static String path = "";
    private static JSONObject file = null;
    private static JSONObject redis_host = null;

    public static String getIndexString() {
        if (!loaded) {
            load();
        }
        return indexString;
    }

    public static void setIndexString(String indexString) {
        DC4.indexString = indexString;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static void setLoaded(boolean loaded) {
        DC4.loaded = loaded;
    }

    public static String getSolrHost() {
        return solrHost;
    }

    public static void setSolrHost(String solrHost) {
        DC4.solrHost = solrHost;
    }

    public static String getPath() {
        return path;
    }

    public static void setPath(String path) {
        DC4.path = path;
    }

    public static JSONObject getFile() {
        return file;
    }

    public static void setFile(JSONObject file) {
        DC4.file = file;
    }

    public static JSONObject getRedis_host() {
        if (!loaded) {
            load();
        }
        return redis_host;
    }

    public static void setRedis_host(JSONObject redis_host) {
        DC4.redis_host = redis_host;
    }

    /**
     * Load properties from configuration file.
     *
     * @return success of the properties loading operation.
     */
    public static boolean load() {
        if (!loaded) {
            try {
                path = DC4.class.getResource("/").getPath();
                JSONParser parser = new JSONParser();
                file = (JSONObject) parser.parse(readFile());
                JSONObject config = (JSONObject) file.get("config");
                solrHost = (String) config.get("solr");
                indexString = (String) config.get("index");
                redis_host = (JSONObject) config.get("redis");
                loaded = true;
            } catch (Exception ex) {
                if (Config.isDebug()) {
                    System.out.println("[DC4][Config] Unable to load DC4 Configuration");
                    Logger.getLogger(DC4.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        return loaded;
    }

    /**
     * Reads JSON configuration file to simple string.
     *
     * @return
     */
    private static String readFile() {
        byte[] buffer = new byte[(int) new File(path + "dc4.js").length()];

        try {
            BufferedInputStream f = null;
            try {
                f = new BufferedInputStream(new FileInputStream(path + "dc4.js"));
                f.read(buffer);
            } finally {
                if (f != null) {
                    try {
                        f.close();
                    } catch (IOException ignored) {
                        Logger.getLogger(DC4.class.getName()).log(Level.SEVERE, null, ignored);
                    }
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(DC4.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new String(buffer);
    }
}
