package pt.ua.bioinformatics.diseasecard.services;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import pt.ua.bioinformatics.coeus.common.Config;

/**
 *
 * @author pedrolopes
 */
public class DC4 {
    private static boolean loaded  = false;
    private static String solrHost = "";
    private static String indexString = "";
    private static String path = "";
    private static JSONObject file = null;

    public static String getIndexString() {
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
    
    public static boolean load() {
        if (!loaded) {
            try {
                path = DC4.class.getResource("/").getPath();                
                JSONParser parser = new JSONParser();
                file = (JSONObject) parser.parse(readFile());
                JSONObject config = (JSONObject) file.get("config");
                solrHost = (String) config.get("solr");  
                indexString = (String) config.get("index");   
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
                    }
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(Config.class.getName()).log(Level.SEVERE, null, ex);
        }
        return new String(buffer);
    }
}
