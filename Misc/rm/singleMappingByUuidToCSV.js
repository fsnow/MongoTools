// passed in variables:
// DB_NAME
// COLL_NAME
// ID (_id of relmig JSON in db/coll)
// UUID (of mapping)

let coll = db.getSiblingDB(DB_NAME)[COLL_NAME];
let filter = {_id: ObjectId(ID)};
var rm = coll.findOne(filter);
var fields = rm.project.content.mappings[UUID].fields;

for (const key in fields) { 
  var field = fields[key];
  var t = field.target;
  var s = field.source;
  print(t.name + "," + t.included + "," + t.isNullExcluded + "," + t.type.toLowerCase() + "," + s.name + "," + s.databaseSpecificType + "," + s.isPrimaryKey);
}

