package pt.ua.diseasecard.service;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openjena.atlas.json.JSON;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import pt.ua.diseasecard.alertBox.AlertBoxValidation;
import pt.ua.diseasecard.components.Boot;
import pt.ua.diseasecard.components.data.SparqlAPI;
import pt.ua.diseasecard.components.data.Storage;
import pt.ua.diseasecard.components.management.Browsier;
import pt.ua.diseasecard.components.management.Cashier;
import pt.ua.diseasecard.components.management.Indexer;
import pt.ua.diseasecard.configuration.DiseasecardProperties;
import pt.ua.diseasecard.configuration.OntologyProperties;
import pt.ua.diseasecard.connectors.CSVFactory;
import pt.ua.diseasecard.connectors.PluginFactory;
import pt.ua.diseasecard.connectors.ResourceFactory;
import pt.ua.diseasecard.connectors.XMLFactory;
import pt.ua.diseasecard.domain.Concept;
import pt.ua.diseasecard.domain.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class DataManagementService {

    private String uploadDir;
    private Storage storage;
    private DiseasecardProperties config;
    private OntologyProperties ontologyProperties;
    private ArrayList<Resource> resources;
    private SparqlAPI sparqlAPI;
    private SimpMessagingTemplate template;
    private AlertBoxValidation alertBoxSchedule;

    private Boot boot;

    private Indexer indexer;
    private Cashier cashier;
    private Browsier browsier;

    public DataManagementService(Storage storage, DiseasecardProperties diseasecardProperties, OntologyProperties ontologyProperties, SparqlAPI sparqlAPI, Boot boot, SimpMessagingTemplate template, Indexer indexer, Cashier cashier, Browsier browsier, AlertBoxValidation alertBoxSchedule) {
        this.uploadDir = "submittedFiles";
        this.storage = storage;
        this.config = diseasecardProperties;
        this.ontologyProperties = ontologyProperties;
        this.resources = new ArrayList<>();
        this.sparqlAPI = sparqlAPI;
        this.boot = boot;
        this.template = template;
        this.indexer = indexer;
        this.cashier = cashier;
        this.browsier = browsier;
        this.alertBoxSchedule = alertBoxSchedule;
    }

    /*
        Description
     */
    public Map<String, String> uploadSetup(MultipartFile file) {
        try
        {
            Path copyLocation = Paths.get(uploadDir + File.separator + StringUtils.cleanPath(file.getOriginalFilename()));
            Files.copy(file.getInputStream(), copyLocation, StandardCopyOption.REPLACE_EXISTING);

            Map<String, String> newEndpoints = this.storage.loadSetup(file.getInputStream());

            return newEndpoints;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }


    /*
        Description
     */
    public void uploadEndpoints(List<MultipartFile> endpoints) throws IOException {
        for (MultipartFile file : endpoints) {
            Path copyLocation = Paths.get(uploadDir + File.separator + "endpoints" + File.separator + StringUtils.cleanPath(file.getOriginalFilename()));
            Files.copy(file.getInputStream(), copyLocation, StandardCopyOption.REPLACE_EXISTING);
        }
        //TODO: Adicionar aqui uma thread
        //this.build();
    }


    /*
        Description
     */
    public void build() {
        try {
            this.storage.setBuildPhase("Building_Initial");

            this.readResources();
            for(Resource r : this.resources)
            {
                try
                {
                    if (r.isBuilt())
                    {
                        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Already built resource " + r.getTitle());
                    }
                    else
                    {
                        if (this.config.getDebug())  Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Reading data for resource " + r.getTitle());
                        this.readData(r);
                    }
                }
                catch (Exception ex)
                {
                    if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Unable to read data for " + this.config.getName() + " in resource " + r.getTitle());
                    Logger.getLogger(DataManagementService.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            this.boot.startInternalProcess();
        }
        catch (Exception ex)
        {
            if(this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Unable to build " + this.config.getName());
            Logger.getLogger(DataManagementService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    public void unbuild() {
        this.resources = new ArrayList<>();
        this.storage.setBuildPhase("Building_Removal");
        this.storage.removeBuild();
        this.browsier.deleteDiseases();
        this.cashier.deleteCache();
        this.browsier.deleteBrowser();
        this.indexer.deleteAllDocuments();
    }


    /*
        Description
     */
    private void readResources() {
        try {
            if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Reading resources for " + this.config.getName());

            JSONArray finalR = getAllResources();

            for (Object o : finalR) {
                JSONObject info = (JSONObject) o;

                Resource r = new Resource((String) info.get("s"), (String) info.get("title"), (String) info.get("label"), (String) info.get("description"), (String) info.get("publisher"), (String) info.get("endpoint"));
                r.setExtendsConcept((String) info.get("extends"));
                r.setIsResourceOf(new Concept((String) info.get("isResourceOf")));
                r.setIdentifiers((String) info.get("identifiers"));
                r.setBuilt((Boolean) info.get("built"));
                r.loadConcept();

                if (( info.get("publisher")).equals("OMIM") || ( info.get("publisher")).equals("omim") ) {
                    r.loadOMIMParser();
                }
                else                                                 r.loadParser();

                this.resources.add(r);
            }
            if (this.config.getDebug())  Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Resource information read");
        }
        catch (Exception ex)
        {
            if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Unable to read resource information");
            Logger.getLogger(DataManagementService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    /*
        Description
     */
    private void readData(Resource r) {
        ResourceFactory factory;
        try
        {
            if (!r.isBuilt())
            {
                switch (r.getPublisher().toLowerCase())
                {
                    case "csv":
                        factory = new CSVFactory(r);
                        break;
                    case "xml":
                        factory = new XMLFactory(r);
                        break;
                    case "omim":
                        factory = new PluginFactory(r);
                        break;
                    default:
                        factory = null;
                }

                if (factory != null)
                {
                    factory.read();
                    factory.save();
                    template.convertAndSend("/topic/message", "Resource " + r.getLabel() + " was built");
                }
            }
            if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Data for " + r.getTitle() + " read");
        } catch (Exception ex) {
            if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Unable to read data for " + r.getTitle());
            Logger.getLogger(DataManagementService.class.getName()).log(Level.SEVERE, null, ex);
        }
    }


    /*
        Description
     */
    public JSONObject getAllEntities() {
        //if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Getting all the existing entities on " + this.config.getName());
        JSONObject finalR = new JSONObject();
        try
        {
            JSONParser parser = new JSONParser();
            JSONObject response = (JSONObject) parser.parse(this.sparqlAPI.select("SELECT ?s ?label ?title ?description ?isEntityOf ?isIncludedIn"
                    + " WHERE { ?s rdf:type coeus:Entity ."
                    + " ?s rdfs:label ?label ."
                    + " ?s dc:description ?description ."
                    + " ?s dc:title ?title ."
                    + "OPTIONAL { ?s coeus:isIncludedIn ?isIncludedIn} . "
                    + "OPTIONAL { ?s coeus:isEntityOf ?isEntityOf}} " , "js", false));

            JSONObject results = (JSONObject) response.get("results");
            JSONArray bindings = (JSONArray) results.get("bindings");

            for (Object o : bindings) {

                JSONObject binding = (JSONObject) o;
                String uri = ((JSONObject) binding.get("s")).get("value").toString();
                String label = ((JSONObject) binding.get("label")).get("value").toString();
                String description = ((JSONObject) binding.get("description")).get("value").toString();
                String title = ((JSONObject) binding.get("title")).get("value").toString();
                String isIncludedIn = ((JSONObject) binding.get("isIncludedIn")).get("value").toString();

                if (!finalR.containsKey(uri)) {
                    finalR.put(uri, new JSONObject());
                    ((JSONObject) finalR.get(uri)).put("label", label);
                    ((JSONObject) finalR.get(uri)).put("uri", uri);
                    ((JSONObject) finalR.get(uri)).put("description", description);
                    ((JSONObject) finalR.get(uri)).put("title", title);
                    ((JSONObject) finalR.get(uri)).put("isIncludedIn", isIncludedIn);
                    ((JSONObject) finalR.get(uri)).put("isEntityOf", new JSONArray());
                }

                JSONObject isEntityOfJSON = (JSONObject) binding.get("isEntityOf");

                if (isEntityOfJSON != null) {
                    ((JSONArray) ((JSONObject) finalR.get(uri)).get("isEntityOf")).add(isEntityOfJSON.get("value").toString());
                }
            }
        }
        catch (ParseException e)
        {
            e.printStackTrace();
        }
        return finalR;
    }


    /*
        Description
     */
    public JSONObject getAllConcepts() {
        //if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Getting all the existing concepts on " + this.config.getName());
        JSONObject finalR = new JSONObject();
        try
        {
            JSONParser parser = new JSONParser();
            JSONObject response = (JSONObject) parser.parse(this.sparqlAPI.select("SELECT ?s ?label ?title ?description ?hasEntity ?hasResource"
                    + " WHERE { ?s rdf:type coeus:Concept ."
                    + " ?s rdfs:label ?label ."
                    + " ?s dc:description ?description ."
                    + " ?s dc:title ?title ."
                    + "OPTIONAL { ?s coeus:hasEntity ?hasEntity} . "
                    + "OPTIONAL { ?s coeus:hasResource ?hasResource}} " , "js", false));

            JSONObject results = (JSONObject) response.get("results");
            JSONArray bindings = (JSONArray) results.get("bindings");

            for (Object o : bindings) {
                JSONObject binding = (JSONObject) o;
                String uri = ((JSONObject) binding.get("s")).get("value").toString();
                String label = ((JSONObject) binding.get("label")).get("value").toString();
                String description = ((JSONObject) binding.get("description")).get("value").toString();
                String title = ((JSONObject) binding.get("title")).get("value").toString();
                String hasEntity = ((JSONObject) binding.get("hasEntity")).get("value").toString();

                if (!finalR.containsKey(uri)) {
                    finalR.put(uri, new JSONObject());

                    ((JSONObject) finalR.get(uri)).put("label", label);
                    ((JSONObject) finalR.get(uri)).put("uri", uri);
                    ((JSONObject) finalR.get(uri)).put("description", description);
                    ((JSONObject) finalR.get(uri)).put("title", title);
                    ((JSONObject) finalR.get(uri)).put("title", title);
                    ((JSONObject) finalR.get(uri)).put("hasEntity", hasEntity);
                    ((JSONObject) finalR.get(uri)).put("hasResource", new JSONArray());
                }

                JSONObject hasResourceJSON = (JSONObject) binding.get("hasResource");

                if (hasResourceJSON != null) {
                    ((JSONArray) ((JSONObject) finalR.get(uri)).get("hasResource")).add(hasResourceJSON.get("value").toString());
                }
            }
        }
        catch (ParseException e)
        {
            e.printStackTrace();
        }

        return finalR;
    }


    /*
        Description
     */
    public JSONArray getAllResources() {
        JSONArray finalR = new JSONArray();
        try
        {
            JSONParser parser = new JSONParser();
            JSONObject response = (JSONObject) parser.parse(this.sparqlAPI.select("SELECT ?s ?isResourceOf ?description ?label ?title ?built ?publisher ?extends ?extension ?order ?endpoint ?built"
                    + " WHERE { ?s rdf:type coeus:Resource ."
                    + " ?s dc:description ?description ."
                    + " ?s rdfs:label ?label ."
                    + " ?s dc:title ?title ."
                    + " ?s dc:publisher ?publisher ."
                    + " ?s coeus:isResourceOf ?isResourceOf ."
                    + " ?s coeus:extends ?extends ."
                    + " ?s coeus:endpoint ?endpoint ."
                    + " ?s coeus:order ?order . "
                    + "OPTIONAL { ?s coeus:built ?built} . "
                    + "OPTIONAL { ?s coeus:identifiers ?identifiers}} "
                    + "ORDER BY ?order", "js", false));
            JSONObject results = (JSONObject) response.get("results");
            JSONArray bindings = (JSONArray) results.get("bindings");

            for (Object o : bindings) {
                JSONObject info = new JSONObject();
                JSONObject binding = (JSONObject) o;

                info.put("s", ((JSONObject) binding.get("s")).get("value").toString());
                info.put("description", ((JSONObject) binding.get("description")).get("value").toString());
                info.put("label", ((JSONObject) binding.get("label")).get("value").toString());
                info.put("title", ((JSONObject) binding.get("title")).get("value").toString());
                info.put("isResourceOf", ((JSONObject) binding.get("isResourceOf")).get("value").toString());
                info.put("publisher", ((JSONObject) binding.get("publisher")).get("value").toString());
                info.put("extends", ((JSONObject) binding.get("extends")).get("value").toString());
                info.put("endpoint", ((JSONObject) binding.get("endpoint")).get("value").toString());
                info.put("order", ((JSONObject) binding.get("order")).get("value").toString());
                info.put("built", Boolean.parseBoolean(((JSONObject) binding.get("built")).get("value").toString()));

                JSONObject identifiers = (JSONObject) binding.get("identifiers");

                if (identifiers != null) info.put("identifiers", identifiers.get("value").toString());
                else info.put("identifiers", "");

                finalR.add(info);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return finalR;
    }


    /*
        Description
     */
    private JSONObject getResource(String resourceURI) {
        JSONObject resource = new JSONObject();
        try
        {
            JSONParser parser = new JSONParser();

            String queryString = "SELECT * "
                + " WHERE { <" + resourceURI + "> dc:title ?title ."
                + " <" + resourceURI + "> dc:description ?description ."
                + " <" + resourceURI + "> rdfs:label ?label ."
                + " <" + resourceURI + "> dc:publisher ?publisher ."
                + " <" + resourceURI + "> coeus:isResourceOf ?isResourceOf ."
                + " <" + resourceURI + "> coeus:extends ?extends ."
                + " <" + resourceURI + "> coeus:endpoint ?endpoint ."
                + " <" + resourceURI + "> coeus:order ?order . "
                + "OPTIONAL { <" + resourceURI + "> coeus:built ?built} . "
                + "OPTIONAL { <" + resourceURI + "> coeus:hasParser ?hasParser} . "
                + "OPTIONAL { <" + resourceURI + "> coeus:identifiers ?identifiers}}";

            JSONObject response = (JSONObject) parser.parse(this.sparqlAPI.select(queryString, "js", false));

            JSONObject results = (JSONObject) response.get("results");
            JSONArray bindings = (JSONArray) results.get("bindings");
            try {
                JSONObject a = (JSONObject) bindings.get(0);
                resource.put("description", ((JSONObject) a.get("description")).get("value").toString());
                resource.put("label", ((JSONObject) a.get("label")).get("value").toString());
                resource.put("title", ((JSONObject) a.get("title")).get("value").toString());
                resource.put("publisher", ((JSONObject) a.get("publisher")).get("value").toString());
                resource.put("isResourceOf", ((JSONObject) a.get("isResourceOf")).get("value").toString());
                resource.put("extends", ((JSONObject) a.get("extends")).get("value").toString());
                resource.put("endpoint", ((JSONObject) a.get("endpoint")).get("value").toString());
                resource.put("order", ((JSONObject) a.get("order")).get("value").toString());

                JSONObject built = (JSONObject) a.get("built");
                JSONObject identifiers = (JSONObject) a.get("identifiers");
                JSONObject hasParser = (JSONObject) a.get("hasParser");

                resource.put("built", built != null ? Boolean.parseBoolean(built.get("value").toString()) : false);
                resource.put("hasParser", hasParser != null ? hasParser.get("value").toString() : "");
                resource.put("identifiers", identifiers != null ? identifiers.get("value").toString() : "");
            } catch (Exception ex) {}
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return resource;
    }


    /*
        Get labels needed in forms: entities_labels, concept_labels, resources_labels, plugins_labels.
        For now, the plugins_labels are going to be hardcoded, since the current ontology is not yet ready.
     */
    public JSONObject getFormLabels(){
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Getting labels needed in forms of " + this.config.getName() + "Admin");
        JSONObject finalR = new JSONObject();
        try
        {
            JSONArray entities = performSimpleQuery("SELECT ?s ?label WHERE { ?s rdf:type coeus:Entity . ?s rdfs:label ?label }");
            JSONObject entitiesLabels = new JSONObject();
            for (Object o : entities) {
                JSONObject binding = (JSONObject) o;
                entitiesLabels.put(((JSONObject) binding.get("s")).get("value").toString(), ((JSONObject) binding.get("label")).get("value").toString());
            }

            JSONArray concepts = performSimpleQuery("SELECT ?s ?label WHERE { ?s rdf:type coeus:Concept . ?s rdfs:label ?label }");
            JSONObject conceptsLabels = new JSONObject();
            for (Object o : concepts) {
                JSONObject binding = (JSONObject) o;
                conceptsLabels.put(((JSONObject) binding.get("s")).get("value").toString(), ((JSONObject) binding.get("label")).get("value").toString());
            }

            JSONArray resource = performSimpleQuery("SELECT ?s ?label WHERE { ?s rdf:type coeus:Resource . ?s rdfs:label ?label }");
            JSONObject resourcesLabels = new JSONObject();
            for (Object o : resource) {
                JSONObject binding = (JSONObject) o;
                resourcesLabels.put(((JSONObject) binding.get("s")).get("value").toString(), ((JSONObject) binding.get("label")).get("value").toString());
            }

            JSONArray orders = performSimpleQuery("SELECT ?s ?order WHERE { ?s coeus:order ?order }");
            TreeSet<Integer> ordersValues = new TreeSet<>();
            TreeSet<Integer> ordersValuesFinal = new TreeSet<>();

            for (Object o : orders) {
                JSONObject binding = (JSONObject) o;
                ordersValues.add(Integer.parseInt(((JSONObject) binding.get("order")).get("value").toString()));
            }

            if ( ordersValues.size() > 0 ) {
                int finalSize = ordersValues.last() + 1;
                for (int i = 0 ; i <= finalSize; i++) {
                    if (!ordersValues.contains(i)) ordersValuesFinal.add(i);
                }
            }

            else ordersValuesFinal.add(0);


            // Has to be changed
            JSONArray pluginsLabels = new JSONArray();
            this.ontologyProperties.getPluginLabels().forEach((element) -> pluginsLabels.add(element));

            finalR.put("entitiesLabels", entitiesLabels);
            finalR.put("conceptsLabels", conceptsLabels);
            finalR.put("resourcesLabels", resourcesLabels);
            finalR.put("pluginsLabels", pluginsLabels);
            finalR.put("ordersLabels", ordersValuesFinal);
        }
        catch (Exception e)  { e.printStackTrace(); }
        return finalR;
    }


    /*
        The idea is to organize all the entities, concepts and resources into a single object, mapping the relations between them.
        This map is then used in DiseasecardAdmin platform.
     */
    public JSONArray getOntologyStructure() {
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Getting Ontology Structure of " + this.config.getName() );

        JSONObject entities = this.getAllEntities();
        JSONObject concepts = this.getAllConcepts();

        for (Object key : entities.keySet()) {
            ((JSONObject) entities.get(key)).put("typeOf", "Entity");
            JSONObject entityValues = (JSONObject) entities.get(key);

            JSONArray entityOf = (JSONArray) entityValues.get("isEntityOf");
            JSONArray auxE = new JSONArray();
            for (Object conceptURI : entityOf) {
                try
                {
                    JSONObject conceptValues = (JSONObject) concepts.get(conceptURI);
                    JSONArray resourcesExtending = (JSONArray) conceptValues.get("hasResource");

                    JSONArray auxR = new JSONArray();

                    for (Object resourceURI : resourcesExtending) {
                        JSONObject resource = this.getResource(resourceURI.toString());
                        resource.put("typeOf", "Resource");
                        resource.put("uri", resourceURI);
                        auxR.add(resource);
                    }
                    ((JSONObject) concepts.get(conceptURI)).put("typeOf", "Concept");
                    ((JSONObject) concepts.get(conceptURI)).replace("hasResource", auxR);
                } catch (Exception ex) { }
                auxE.add(concepts.get(conceptURI));
            }
            ((JSONObject) entities.get(key)).replace("isEntityOf", auxE);
        }

        JSONArray results = new JSONArray();

        entities.forEach( (key,value) -> { results.add(value); } );

        return results;
    }


    public JSONObject getSimplifiedOntologyStructure() {
        String baseUrl = this.config.getPrefixes().get("diseasecard");

        JSONObject ontology = new JSONObject();

        ontology.put("name", this.storage.getSeedURI().replace(baseUrl, ""));
        ontology.put("children", new JSONArray());

        JSONObject entities = this.getAllEntities();
        JSONObject concepts = this.getAllConcepts();

        for (Object key : entities.keySet()) {

            JSONObject infoEntity = new JSONObject();
            infoEntity.put("name", ((JSONObject) entities.get(key)).get("label"));
            infoEntity.put("children", new JSONArray());

            JSONArray entityOf = (JSONArray) ((JSONObject) entities.get(key)).get("isEntityOf");
            for (Object conceptURI : entityOf)
            {
                JSONObject infoConcept = new JSONObject();
                infoConcept.put("name", ((JSONObject) concepts.get(conceptURI)).get("label"));
                infoConcept.put("children", new JSONArray());

                JSONArray resourcesExtending = (JSONArray) ((JSONObject) concepts.get(conceptURI)).get("hasResource");

                for (Object resourceURI : resourcesExtending)
                {
                    JSONObject resource = this.getResource(resourceURI.toString());
                    JSONObject infoResource = new JSONObject();
                    infoResource.put("name", resource.get("label"));
                    infoResource.put("value", 20);

                    ((JSONArray) infoConcept.get("children")).add(infoResource);
                }

                ((JSONArray) infoEntity.get("children")).add(infoConcept);
            }

            ((JSONArray) ontology.get("children")).add(infoEntity);

        }
        return ontology;
    }


    private JSONArray performSimpleQuery(String query) {
        JSONArray bindings = new JSONArray();
        try
        {
            JSONParser parser = new JSONParser();
            JSONObject response = (JSONObject) parser.parse(this.sparqlAPI.select( query , "js", false));

            JSONObject results = (JSONObject) response.get("results");
            bindings = (JSONArray) results.get("bindings");
        }
        catch (Exception e)  { e.printStackTrace(); }
        return bindings;
    }


    public void prepareAddEntity(String title, String label, String description, String entityOf) {
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Add Entity to " + this.config.getName() );

        this.storage.addEntity(title, label, description, entityOf);
    }


    public void prepareAddConcept(String title, String label, String description, String hasEntity, String hasResource) {
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[Diseasecard][DataManagementService] Add Concept to " + this.config.getName() );

        this.storage.addConcept(title, label, description, hasEntity, hasResource);
    }

    /*
        Prepare info to add a Resource to the model.
        This method is used when the endpoint to be added is a file.
        In this method the file is also stored into the uploadDir/endpoints and this location (plus the file name) is then used in the model.
     */
    public void prepareAddResource(String title, String label, String description, String resourceOf, String extendsResource, String order, String publisher, MultipartFile file) {
        try {
            Path copyLocation = Paths.get(uploadDir + File.separator + "endpoints" + File.separator + StringUtils.cleanPath(label));
            Files.copy(file.getInputStream(), copyLocation, StandardCopyOption.REPLACE_EXISTING);

            this.storage.addResource(title, label, description, resourceOf, extendsResource, order, publisher, copyLocation.toString());
        } catch (IOException ex) {
            Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Error while processing endpoint of resource");
        }
    }


    /*
        Prepare info to add a Resource to the model.
        This method is used when the endpoint to be added is an URL.
     */
    public void prepareAddResource(String title, String label, String description, String resourceOf, String extendsResource, String order, String publisher, String endpoint) {
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[Diseasecard][DataManagementService] Add Resource to " + this.config.getName() );
        this.storage.addResource(title, label, description, resourceOf, extendsResource, order, publisher, endpoint);
    }


    public void prepareAddOMIMResource(String title, String label, String description, String resourceOf, String extendsResource, String order, String publisher, MultipartFile morbidmap, MultipartFile genemap) {
        this.saveOMIMEndpoints(genemap, morbidmap);
        this.storage.addResource(title, label, description, resourceOf, extendsResource, order, publisher, "omim://full");
    }



    public void prepareAddSourceBaseURL(String resourceLabel, String baseURL) {
//        this.storage.addSourceBaseURL(resourceLabel.split("_")[1].toLowerCase(), baseURL);
        this.storage.addSourceBaseURL(resourceLabel, baseURL);
    }


    /*
        Prepare info to add a Parser to the model.
     */
    public void prepareAddParser(String resource, String resourceID, String regexResource, String externalResourceID, String regexExternalResource) {
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[Diseasecard][DataManagementService] Add Parser to " + resource );

        this.storage.addParserCore(resource, false, resourceID, regexResource, false, externalResourceID, regexExternalResource);
    }


    public void prepareAddParser(String resource, String mainNode, String isMethodByReplace, String resourceInfoInAttribute, String resourceInfo, String uniqueResource, String regexResource, String externalResourceInfoInAttribute, String externalResourceInfo, String externalResourceNode, String regexExternalResource, String uniqueExternalResource,String filterBy, String filterValue ) {
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[Diseasecard][DataManagementService] Add Parser to " + resource );

        this.storage.addParserCore(resource, Boolean.parseBoolean(resourceInfoInAttribute), resourceInfo, regexResource, Boolean.parseBoolean(externalResourceInfoInAttribute), externalResourceInfo, regexExternalResource);
        this.storage.addParserExtra(resource, mainNode, isMethodByReplace, uniqueResource, externalResourceNode, uniqueExternalResource, filterBy, filterValue);
    }


    public void prepareAddOMIMParser(String resource, String genecardName, String genecardOMIM, String genecardLocation, String genecardGenes, String morbidmapName, String morbidmapGene, String morbidmapOMIM, String morbidmapLocation) {
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[Diseasecard][DataManagementService] Add Parser to " + resource );

        this.storage.addOMIMParser(resource,genecardName,genecardOMIM,genecardLocation,genecardGenes,morbidmapName,morbidmapGene,morbidmapOMIM,morbidmapLocation);
    }


    public void prepareEditInstance(Map<String, String> allParams) {
        String typeOf = allParams.get("typeOf");
        String uri = allParams.get("uri");

        allParams.remove("typeOf");
        allParams.remove("uri");

        switch (typeOf) {
            case "Entity":
                this.storage.editEntity(uri, allParams);
                break;
            case "Concept":
                this.storage.editConcept(uri, allParams);
                break;
            case "Resource":
                this.storage.editResource(uri, allParams);
                break;
        }
    }


    public void prepareEditResourceSingleEndpoint(Map<String, String> allParams, MultipartFile file) {
        String label = allParams.get("label");
        String uri = allParams.get("uri");

        System.out.println("PREPARE EDIT RESOURCE SINGLE ENDPOINT\n" + allParams.entrySet());

        try {
            Path copyLocation = Paths.get(uploadDir + File.separator + "endpoints" + File.separator + StringUtils.cleanPath(label));
            Files.copy(file.getInputStream(), copyLocation, StandardCopyOption.REPLACE_EXISTING);

            allParams.put("endpoint", copyLocation.toString());
            allParams.remove("typeOf");
            allParams.remove("uri");

            this.storage.editResource(uri, allParams);

        } catch (IOException ex) {
            Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Error while processing endpoint of resource");
        }
    }


    public void prepareEditResourceOMIM(Map<String, String> allParams, MultipartFile genemap, MultipartFile morbidmap) {
        String uri = allParams.get("uri");

        System.out.println("PREPARE EDIT RESOURCE OMIM\n" + allParams.entrySet());

        this.saveOMIMEndpoints(genemap, morbidmap);

        allParams.put("endpoint", "omim://full");
        allParams.remove("typeOf");
        allParams.remove("uri");
        this.storage.editResource(uri, allParams);
    }


    public void prepareEditSourceBaseURL(String label, String baseURL) {
        this.storage.editSourceBaseURL(label, baseURL);
    }


    public void removeInstance(String typeOf, String uri) {
        if (this.config.getDebug()) Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Remove Instance with " + uri );

        switch (typeOf) {
            case "Entity":
                this.storage.removeEntity(uri);
                break;
            case "Concept":
                this.storage.removeConcept(uri);
                break;
            case "Resource":
                this.storage.removeResource(uri);
                break;
        }
    }


    public void prepareRemoveSourceBaseURL(String resourceLabel) {
        this.storage.removeSourceBaseURL(resourceLabel);
    }


    public String getSystemStatus() {
        JSONArray phase = performSimpleQuery("SELECT ?phase WHERE { diseasecard:builtStatus coeus:systemBuiltPhase ?phase }");
        String p = null;

        for (Object o : phase) {
            JSONObject binding = (JSONObject) o;
            p = ((JSONObject) binding.get("phase")).get("value").toString();
        }

        return p;
    }

    /*
        This method allows to retrieve all concept's labels.
        This information is currently used by the Tree component at Disease Page.
     */
    public JSONArray getTreeStructure() {

        JSONArray results = new JSONArray();

        /*
        SELECT ?label
        WHERE {
             ?s rdf:type coeus:Concept .
             ?s rdfs:label ?label
        }
        ORDER BY ASC(UCASE(str(?label)))
         */

        JSONArray queryResults = performSimpleQuery("SELECT ?label WHERE { ?s rdf:type coeus:Concept . ?s rdfs:label ?label } ORDER BY ASC(UCASE(str(?label)))");

        for (Object o : queryResults) {
            JSONObject binding = (JSONObject) o;
            results.add(((JSONObject) binding.get("label")).get("value").toString());
        }

        return results;
    }


    public JSONArray queryJenaModel(String query) throws ParseException {
        JSONParser parser = new JSONParser();
        JSONObject response = (JSONObject) parser.parse(this.sparqlAPI.select( query , "js", false));
        JSONObject results = (JSONObject) response.get("results");
        return (JSONArray) results.get("bindings");
    }


    public JSONArray getPrefixes() {
        Map<String, String> prefixes = this.ontologyProperties.getPrefixes();
        JSONArray results = new JSONArray();

        for (Map.Entry<String,String> entry : prefixes.entrySet()) {
            JSONObject o = new JSONObject();
            o.put("prefix", entry.getKey());
            o.put("uri", entry.getValue());
            results.add(o);
        }

        return results;
    }


    public JSONArray getSources() {

        JSONArray finalR = new JSONArray();
        try
        {
            JSONParser parser = new JSONParser();
            JSONObject response = (JSONObject) parser.parse(this.sparqlAPI.select("SELECT *"
                    + " WHERE { ?s rdf:type coeus:SourceBaseURL ."
                    + " ?s rdfs:label ?source ."
                    + " ?s coeus:baseURL ?url } ", "js", false));

            JSONObject results = (JSONObject) response.get("results");
            JSONArray bindings = (JSONArray) results.get("bindings");

            for (Object o : bindings) {
                JSONObject info = new JSONObject();
                JSONObject binding = (JSONObject) o;

                info.put("source", ((JSONObject) binding.get("source")).get("value").toString());
                info.put("url", ((JSONObject) binding.get("url")).get("value").toString());

                finalR.add(info);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return finalR;
    }


    public JSONArray getResourcesWithoutBaseURL() {
        JSONArray finalR = new JSONArray();
        try
        {
            /*
                SELECT *
                WHERE { ?s rdf:type coeus:Resource .
                OPTIONAL { ?s coeus:hasBaseURL ?source . }
                FILTER( !bound(?source))
                }
             */

            JSONParser parser = new JSONParser();
            JSONObject response = (JSONObject) parser.parse(this.sparqlAPI.select("SELECT *"
                    + " WHERE { ?s rdf:type coeus:Resource ."
                    + " ?s rdfs:label ?label ."
                    + " OPTIONAL { ?s coeus:hasBaseURL ?source . }"
                    + " FILTER( !bound(?source)) }", "js", false));

            JSONObject results = (JSONObject) response.get("results");
            JSONArray bindings = (JSONArray) results.get("bindings");

            for (Object o : bindings) {
                JSONObject binding = (JSONObject) o;
                finalR.add(((JSONObject) binding.get("label")).get("value").toString());
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return finalR;
    }


    /*
        SELECT *
        WHERE {
            ?s rdf:type coeus:SourceBaseURLError .
            ?s coeus:url ?url .
            ?s coeus:error ?error .
            ?s coeus:source ?source .
        }
     */
    public JSONObject getAlertBoxResults() {

        JSONObject finalResults = new JSONObject();
        JSONArray results = new JSONArray();

        JSONArray queryResults = performSimpleQuery("SELECT * WHERE { ?s rdf:type coeus:SourceBaseURLError . ?s coeus:url ?url . ?s coeus:error ?error . ?s coeus:source ?source . }");

        int totalErrors = 0;

        JSONObject data = new JSONObject();

        for (Object o : queryResults) {
            JSONObject binding = (JSONObject) o;
            JSONObject info = new JSONObject();

            String source = ((JSONObject) binding.get("source")).get("value").toString();

            info.put("url", ((JSONObject) binding.get("url")).get("value").toString());
            info.put("error", ((JSONObject) binding.get("error")).get("value").toString());

            //results.add(info);

            if (!data.containsKey(source)) data.put(source, new JSONArray());
            ((JSONArray) data.get(source)).add(info);
            totalErrors++ ;
        }

        JSONArray graphLabels = new JSONArray();
        JSONArray graphData = new JSONArray();

        data.forEach( (key, value) -> {

            JSONObject aux = new JSONObject();

            aux.put("source", key);
            aux.put("numberOfErrors", ((JSONArray) value).size());
            aux.put("info", value);

            graphData.add(((JSONArray) value).size());
            graphLabels.add(key);

            results.add(aux);
        });

        finalResults.put("list", results);
        finalResults.put("totalErrors", totalErrors);
        finalResults.put("graphData", graphData);
        finalResults.put("graphLabels", graphLabels);
        finalResults.put("status", this.storage.getValidationDetails());

        return finalResults;
    }


    public JSONObject validateDiseaseEndpoints(String d)  {

        if (d == null) return new JSONObject();

        try {
            JSONParser parser = new JSONParser();
            JSONObject json = (JSONObject) parser.parse(d);

            return this.alertBoxSchedule.diseaseRealTimeValidation(json);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return null;
    }


    private void saveOMIMEndpoints(MultipartFile genemap, MultipartFile morbidmap) {
        try {
            Path copyLocationGenemap = Paths.get(uploadDir + File.separator + "endpoints" + File.separator + "omim_genemap");
            Files.copy(genemap.getInputStream(), copyLocationGenemap, StandardCopyOption.REPLACE_EXISTING);

            Path copyLocationMorbidmap = Paths.get(uploadDir + File.separator + "endpoints" + File.separator + "omim_morbidmap");
            Files.copy(morbidmap.getInputStream(), copyLocationMorbidmap, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            Logger.getLogger(DataManagementService.class.getName()).log(Level.INFO,"[COEUS][DataManagementService] Error while processing endpoint of resource");
        }
    }


    public void validateAllEndpoints() {
        this.alertBoxSchedule.searchInvalidItems();
    }


    public void validateEndpoints() {
        this.alertBoxSchedule.lightValidation();
    }


    public JSONObject getInstancesCount() {

        JSONObject info = new JSONObject();

        // Get number of entities
        JSONArray results = performSimpleQuery("SELECT ?s (count(distinct ?s) as ?count) WHERE { ?s rdf:type coeus:Entity }");
        if (results.size() > 0) info.put("numberOfEntities", ((JSONObject) ((JSONObject) results.get(0)).get("count")).get("value").toString());
        else                    info.put("numberOfEntities", "--");


        // Get number of concepts
        results = performSimpleQuery("SELECT ?s (count(distinct ?s) as ?count) WHERE { ?s rdf:type coeus:Concept }");
        if (results.size() > 0) info.put("numberOfConcepts", ((JSONObject) ((JSONObject) results.get(0)).get("count")).get("value").toString());
        else                    info.put("numberOfConcepts", "--");

        // Get number of resources
        results = performSimpleQuery("SELECT ?s (count(distinct ?s) as ?count) WHERE { ?s rdf:type coeus:Resource }");
        if (results.size() > 0) info.put("numberOfResources", ((JSONObject) ((JSONObject) results.get(0)).get("count")).get("value").toString());
        else                    info.put("numberOfResources", "--");


        // Get number of Items
        results = performSimpleQuery("SELECT ?s (count(distinct ?s) as ?count) WHERE { ?s rdf:type diseasecard:Item }");
        if (results.size() > 0) info.put("numberOfItems", ((JSONObject) ((JSONObject) results.get(0)).get("count")).get("value").toString());
        else                    info.put("numberOfItems", "--");


        // Get number of Items
        results = performSimpleQuery("SELECT ?s (count(distinct ?s) as ?count) WHERE { ?s rdf:type coeus:SourceBaseURLError }");
        if (results.size() > 0) info.put("numberOfInvalidItems", ((JSONObject) ((JSONObject) results.get(0)).get("count")).get("value").toString());
        else                    info.put("numberOfInvalidItems", "--");


        // Get number of items per concept
        results = performSimpleQuery("SELECT ?label ?item (count(distinct ?item) as ?itemCount) WHERE {"
                + " ?s rdf:type coeus:Concept . "
                + " ?s rdfs:label ?label . "
                + " ?s coeus:isConceptOf ?item } GROUP BY ?label");

        List<String> labels = new ArrayList<>();
        List<Integer> itemsPerConcept = new ArrayList<>();

        for (Object o : results) {
            JSONObject binding = (JSONObject) o;

            if (binding.containsKey("label")) {
                String label = ( (JSONObject) binding.get("label")).get("value").toString();

                labels.add(label.substring(label.lastIndexOf("_") + 1) );
                itemsPerConcept.add(Integer.parseInt(( (JSONObject) binding.get("itemCount")).get("value").toString()));
            }


        }

        List<Integer> invalidItemsPerConcept = new ArrayList<>();

        if (labels.size() > 0) {
            invalidItemsPerConcept = new ArrayList<Integer>(Collections.nCopies(itemsPerConcept.size()-1, 0));

            results = performSimpleQuery("SELECT ?label ?e (count(distinct ?e) as ?itemCount) WHERE {"
                    + " ?s rdf:type coeus:Resource . "
                    + " ?s coeus:isResourceOf ?c . "
                    + " ?e coeus:hasResource ?s . "
                    + " ?e rdf:type coeus:SourceBaseURLError . "
                    + " ?c rdfs:label ?label . } GROUP BY ?label");


            for (Object o : results) {
                JSONObject binding = (JSONObject) o;

                if (binding.containsKey("label")) {
                    String label = ( (JSONObject) binding.get("label")).get("value").toString();
                    Integer itemCount = Integer.parseInt(( (JSONObject) binding.get("itemCount")).get("value").toString());

                    if (labels.contains(label.substring(label.lastIndexOf("_") + 1))) {
                        int index = labels.indexOf(label.substring(label.lastIndexOf("_") + 1));
                        invalidItemsPerConcept.add(index, itemCount);
                    }
                }

            }
        }



        info.put("graphLabels", labels);
        info.put("validItems", itemsPerConcept);
        info.put("invalidItems", invalidItemsPerConcept);

        return info;
    }
}