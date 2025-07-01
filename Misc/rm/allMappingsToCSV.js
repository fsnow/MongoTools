// passed in variables:
// DB_NAME
// COLL_NAME
// ID (_id of relmig JSON in db/coll)


let coll = db.getSiblingDB(DB_NAME)[COLL_NAME];
let filter = {_id: ObjectId(ID)};
var rm = coll.findOne(filter);

var m = rm.project.content.mappings;
var newDocUuids = Object.keys(m).filter(uuid => m[uuid].settings.type === "NEW_DOCUMENT");

for (var uuid of newDocUuids) {
  print();
  print(uuid);
  var fields = m[uuid].fields;
  var children
  for (const key in fields) { 
    var field = fields[key];
    var t = field.target;
    var s = field.source;
    print(t.name + "," + t.included + "," + t.isNullExcluded + "," + t.type.toLowerCase() + "," + s.name + "," + s.databaseSpecificType + "," + s.isPrimaryKey);
  }
}


