// passed in variables:
// DB_NAME
// COLL_NAME
// ID (_id of relmig JSON in db/coll)
// UUID (of collection)

let coll = db.getSiblingDB(DB_NAME)[COLL_NAME];
let filter = {_id: ObjectId(ID)};
var rm = coll.findOne(filter);

var collUuid = UUID;
var collName = rm.project.content.collections[collUuid].name;

var collMappingUuids = [];
var newDocumentMappingUuid = null;
for (const mappingUuid in rm.project.content.mappings) {
  var mapping = rm.project.content.mappings[mappingUuid];
  if (mapping.collectionId == collUuid) {
    collMappingUuids.push(mappingUuid);
    if (mapping.settings.type === "NEW_DOCUMENT") {
      newDocumentMappingUuid = mappingUuid;
    }
  }
}

var fields = rm.project.content.mappings[newDocumentMappingUuid].fields;

for (const key in fields) { 
  var field = fields[key];
  var t = field.target;
  var s = field.source;
  print(t.name + "," + t.included + "," + t.isNullExcluded + "," + t.type.toLowerCase() + "," + s.name + "," + s.databaseSpecificType + "," + s.isPrimaryKey);
}

