curl -H 'Content-Type: application/json' -d '
{
        "id":"myid",
        "pluginId":"ciao",
        "pluginURL":"http://pippo.log",
	"policy":"PUBLIC",

        "semanticDescription":{
                "connectedTo":"INNK_TAG_CONNECTED_TO",
                "agentType":"SDEV",
                "hasResource":[
                        {"id":"light",          "interworkingServiceURL":"INNK_TAG_SERVICE_URL",    "locatedAt":"INNK_TAG_LOCATED_AT"},
                        {"id":"temperatur",          "interworkingServiceURL":"INNK_TAG_SERVICE_URL",    "locatedAt":"INNK_TAG_LOCATED_AT"}
                ]}
}' -X POST -D - http://localhost:8080/innkeeper/join;
~                                                          