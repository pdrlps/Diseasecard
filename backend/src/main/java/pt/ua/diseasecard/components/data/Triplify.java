package pt.ua.diseasecard.components.data;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import pt.ua.diseasecard.configuration.DiseasecardProperties;
import pt.ua.diseasecard.connectors.plugins.HGNCPlugin;
import pt.ua.diseasecard.domain.Resource;
import pt.ua.diseasecard.utils.ConceptFactory;
import pt.ua.diseasecard.utils.PrefixFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Configurable
public class Triplify {


    private HashMap<String, ArrayList<String>> properties;
    private Resource resource;
    private String extension;

    @Autowired
    private SparqlAPI api;

    @Autowired
    private DiseasecardProperties config;

    @Autowired
    private Storage storage;


    public Triplify(Resource resource, String extension) {
        this.resource = resource;
        this.extension = extension;
        this.properties = new HashMap<String, ArrayList<String>>();
    }

    public void itemize(String i) {

        Map<String, String> prefixes = this.config.getPrefixes();

        try {
            // create initial Item triple with <concept>_<key> structure
            String[] itemTmp = resource.getIsResourceOf().getUri().split("_");
            com.hp.hpl.jena.rdf.model.Resource item = api.createResource(PrefixFactory.getURIForPrefix(this.config.getKeyprefix()) + itemTmp[1] + "_" + i);
            com.hp.hpl.jena.rdf.model.Resource obj = api.createResource(PrefixFactory.getURIForPrefix(this.config.getKeyprefix()) + "Item");
            api.addStatement(item, this.storage.getProperty(prefixes.get("rdf:type")), obj);

            // set Item label and creator
            api.addStatement(item, this.storage.getProperty(prefixes.get("rdfs:label")), ConceptFactory.getTokenFromConcept(this.resource.getIsResourceOf().getLabel()) + i);
            api.addStatement(item, this.storage.getProperty(prefixes.get("dc:creator")), this.config.getName());

            // associate Item with Concept
            com.hp.hpl.jena.rdf.model.Resource con = api.getResource(resource.getIsResourceOf().getUri());
            api.addStatement(item, this.storage.getProperty(prefixes.get("coeus:hasConcept")), con);
            api.addStatement(con, this.storage.getProperty(prefixes.get("coeus:isConceptOf")), item);

            // associate with other Item
            if (!extension.equals("")) {
                com.hp.hpl.jena.rdf.model.Resource it = api.getResource(extension);
                api.addStatement(item, this.storage.getProperty(prefixes.get("coeus:isAssociatedTo")), it);
                api.addStatement(it, this.storage.getProperty(prefixes.get("coeus:isAssociatedTo")), item);
            }

            // set Resource-specific properties (from HashMap)
            for (String key : properties.keySet()) {
                for (String object : properties.get(key)) {
                    api.addStatement(item, this.storage.getProperty(prefixes.get(key)), object);
                }
            }
        }
        catch (Exception ex)
        {
            if (this.config.getDebug()) System.out.println("[COEUS][Triplify] Unable to add item to " + resource.getTitle());
            Logger.getLogger(Triplify.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void add(String pred, String obj) {
        if (!properties.containsKey(pred)) {
            properties.put(pred, new ArrayList<String>());
        }
        properties.get(pred).add(obj);
    }
}
