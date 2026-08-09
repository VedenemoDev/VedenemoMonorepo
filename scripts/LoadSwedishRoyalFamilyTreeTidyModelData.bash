#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

API_BASE_URL="${VEDENEMO_API_BASE_URL:-http://127.0.0.1:8080}"
MODEL_AZ_NAME="FamilyTreeTidy"
MODEL_INSTANCE_ROOT_NAME="Swedish Royal Family Tree Tidy"
VDOS_FILE="${REPO_ROOT}/.vedenemo/FamilyTreeTidy.vdos"

python3 - "$API_BASE_URL" "$MODEL_AZ_NAME" "$MODEL_INSTANCE_ROOT_NAME" "$VDOS_FILE" <<'PY'
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

api_base_url, model_az_name, model_instance_root_name, vdos_file = sys.argv[1:]
api_base_url = api_base_url.rstrip("/")

PERSONS = [
    {"key": "gustaf_adolf", "GivenNames": "Gustaf Adolf Oscar Fredrik Arthur Edmund", "FamilyName": "Bernadotte", "BirthDate": "1906-04-22", "DeathDate": "1947-01-26", "DisplayName": "Gustaf Adolf", "Notes": "Hereditary Prince of Sweden; father of King Carl XVI Gustaf."},
    {"key": "sibylla", "GivenNames": "Sibylla Calma Marie Alice Bathildis Feodora", "FamilyName": "Saxe-Coburg and Gotha", "BirthDate": "1908-01-18", "DeathDate": "1972-11-28", "DisplayName": "Sibylla", "Notes": "Princess Sibylla; mother of King Carl XVI Gustaf."},
    {"key": "margaretha", "GivenNames": "Margaretha Desiree Victoria", "FamilyName": "Bernadotte", "BirthDate": "1934-10-31", "DeathDate": "", "DisplayName": "Princess Margaretha", "Notes": "Oldest sister of King Carl XVI Gustaf."},
    {"key": "birgitta", "GivenNames": "Birgitta Ingeborg Alice", "FamilyName": "Bernadotte", "BirthDate": "1937-01-19", "DeathDate": "2024-12-04", "DisplayName": "Princess Birgitta", "Notes": "Sister of King Carl XVI Gustaf."},
    {"key": "desiree", "GivenNames": "Desiree Elisabeth Sibylla", "FamilyName": "Bernadotte", "BirthDate": "1938-06-02", "DeathDate": "", "DisplayName": "Princess Desiree", "Notes": "Sister of King Carl XVI Gustaf."},
    {"key": "christina", "GivenNames": "Christina Louise Helena", "FamilyName": "Bernadotte", "BirthDate": "1943-08-03", "DeathDate": "", "DisplayName": "Princess Christina", "Notes": "Youngest sister of King Carl XVI Gustaf."},
    {"key": "carl_xvi_gustaf", "GivenNames": "Carl Gustaf Folke Hubertus", "FamilyName": "Bernadotte", "BirthDate": "1946-04-30", "DeathDate": "", "DisplayName": "Carl XVI Gustaf", "Notes": "King of Sweden since 1973-09-15."},
    {"key": "silvia", "GivenNames": "Silvia Renate", "FamilyName": "Sommerlath", "BirthDate": "1943-12-23", "DeathDate": "", "DisplayName": "Queen Silvia", "Notes": "Queen Silvia of Sweden."},
    {"key": "victoria", "GivenNames": "Victoria Ingrid Alice Desiree", "FamilyName": "Bernadotte", "BirthDate": "1977-07-14", "DeathDate": "", "DisplayName": "Crown Princess Victoria", "Notes": "Heir to the Swedish throne."},
    {"key": "daniel", "GivenNames": "Olof Daniel", "FamilyName": "Westling", "BirthDate": "1973-09-15", "DeathDate": "", "DisplayName": "Prince Daniel", "Notes": "Husband of Crown Princess Victoria."},
    {"key": "estelle", "GivenNames": "Estelle Silvia Ewa Mary", "FamilyName": "Bernadotte", "BirthDate": "2012-02-23", "DeathDate": "", "DisplayName": "Princess Estelle", "Notes": "Oldest child of Crown Princess Victoria and Prince Daniel."},
    {"key": "oscar", "GivenNames": "Oscar Carl Olof", "FamilyName": "Bernadotte", "BirthDate": "2016-03-02", "DeathDate": "", "DisplayName": "Prince Oscar", "Notes": "Second child of Crown Princess Victoria and Prince Daniel."},
    {"key": "carl_philip", "GivenNames": "Carl Philip Edmund Bertil", "FamilyName": "Bernadotte", "BirthDate": "1979-05-13", "DeathDate": "", "DisplayName": "Prince Carl Philip", "Notes": "Son of King Carl XVI Gustaf and Queen Silvia."},
    {"key": "sofia", "GivenNames": "Sofia Kristina", "FamilyName": "Hellqvist", "BirthDate": "1984-12-06", "DeathDate": "", "DisplayName": "Princess Sofia", "Notes": "Wife of Prince Carl Philip."},
    {"key": "alexander", "GivenNames": "Alexander Erik Hubertus Bertil", "FamilyName": "Bernadotte", "BirthDate": "2016-04-19", "DeathDate": "", "DisplayName": "Prince Alexander", "Notes": "Child of Prince Carl Philip and Princess Sofia."},
    {"key": "gabriel", "GivenNames": "Gabriel Carl Walther", "FamilyName": "Bernadotte", "BirthDate": "2017-08-31", "DeathDate": "", "DisplayName": "Prince Gabriel", "Notes": "Child of Prince Carl Philip and Princess Sofia."},
    {"key": "julian", "GivenNames": "Julian Herbert Folke", "FamilyName": "Bernadotte", "BirthDate": "2021-03-26", "DeathDate": "", "DisplayName": "Prince Julian", "Notes": "Child of Prince Carl Philip and Princess Sofia."},
    {"key": "ines", "GivenNames": "Ines Marie Lilian Silvia", "FamilyName": "Bernadotte", "BirthDate": "2025-02-07", "DeathDate": "", "DisplayName": "Princess Ines", "Notes": "Youngest child of Prince Carl Philip and Princess Sofia."},
    {"key": "madeleine", "GivenNames": "Madeleine Therese Amelie Josephine", "FamilyName": "Bernadotte", "BirthDate": "1982-06-10", "DeathDate": "", "DisplayName": "Princess Madeleine", "Notes": "Daughter of King Carl XVI Gustaf and Queen Silvia."},
    {"key": "christopher", "GivenNames": "Christopher Paul", "FamilyName": "O'Neill", "BirthDate": "1974-06-27", "DeathDate": "", "DisplayName": "Christopher O'Neill", "Notes": "Husband of Princess Madeleine."},
    {"key": "leonore", "GivenNames": "Leonore Lilian Maria", "FamilyName": "Bernadotte", "BirthDate": "2014-02-20", "DeathDate": "", "DisplayName": "Princess Leonore", "Notes": "Child of Princess Madeleine and Christopher O'Neill."},
    {"key": "nicolas", "GivenNames": "Nicolas Paul Gustaf", "FamilyName": "Bernadotte", "BirthDate": "2015-06-15", "DeathDate": "", "DisplayName": "Prince Nicolas", "Notes": "Child of Princess Madeleine and Christopher O'Neill."},
    {"key": "adrienne", "GivenNames": "Adrienne Josephine Alice", "FamilyName": "Bernadotte", "BirthDate": "2018-03-09", "DeathDate": "", "DisplayName": "Princess Adrienne", "Notes": "Child of Princess Madeleine and Christopher O'Neill."},
]

FAMILY_UNITS = [
    {"key": "gustaf_adolf_sibylla", "Label": "Gustaf Adolf + Sibylla", "StartDate": "1932-10-20", "EndDate": "1947-01-26", "RelationType": "Marriage", "Notes": "Marriage of Prince Gustaf Adolf and Princess Sibylla.", "spouses": ["gustaf_adolf", "sibylla"], "children": ["margaretha", "birgitta", "desiree", "christina", "carl_xvi_gustaf"]},
    {"key": "carl_silvia", "Label": "Carl XVI Gustaf + Silvia", "StartDate": "1976-06-19", "EndDate": "", "RelationType": "Marriage", "Notes": "Marriage of King Carl XVI Gustaf and Queen Silvia.", "spouses": ["carl_xvi_gustaf", "silvia"], "children": ["victoria", "carl_philip", "madeleine"]},
    {"key": "victoria_daniel", "Label": "Victoria + Daniel", "StartDate": "2010-06-19", "EndDate": "", "RelationType": "Marriage", "Notes": "Marriage of Crown Princess Victoria and Prince Daniel.", "spouses": ["victoria", "daniel"], "children": ["estelle", "oscar"]},
    {"key": "carl_philip_sofia", "Label": "Carl Philip + Sofia", "StartDate": "2015-06-13", "EndDate": "", "RelationType": "Marriage", "Notes": "Marriage of Prince Carl Philip and Princess Sofia.", "spouses": ["carl_philip", "sofia"], "children": ["alexander", "gabriel", "julian", "ines"]},
    {"key": "madeleine_christopher", "Label": "Madeleine + Christopher", "StartDate": "2013-06-08", "EndDate": "", "RelationType": "Marriage", "Notes": "Marriage of Princess Madeleine and Christopher O'Neill.", "spouses": ["madeleine", "christopher"], "children": ["leonore", "nicolas", "adrienne"]},
]

ROOT_FAMILY_UNITS = ["gustaf_adolf_sibylla"]
FAMILY_NODE_ENTITIES = ["FamilyUnitNode0", "FamilyUnitNode2", "FamilyUnitNode4", "FamilyUnitNode6"]
PERSON_NODE_ENTITIES = ["PersonNode1", "PersonNode3", "PersonNode5", "PersonNode7"]


def request(method, path, body=None, content_type="application/json"):
    data = None
    headers = {}
    if body is not None:
        if isinstance(body, str):
            data = body.encode("utf-8")
        else:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = content_type
    req = urllib.request.Request(api_base_url + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            return response.status, response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8")
    except urllib.error.URLError as error:
        raise SystemExit(f"Could not connect to {api_base_url}: {error.reason}") from error


def require_success(status, body, action):
    if status < 200 or status >= 300:
        raise SystemExit(f"{action} failed with HTTP {status}: {body}")


def json_body(status, body, action):
    require_success(status, body, action)
    return json.loads(body)


def quoted(value):
    return urllib.parse.quote(value, safe="")


def root_path(path):
    return f"/data/{quoted(model_az_name)}/roots/{quoted(instance_root_id)}{path}"


def find_first_instance(entity_az_name, filters):
    query = urllib.parse.urlencode(filters)
    status, body = request("GET", root_path(f"/{quoted(entity_az_name)}?{query}"))
    instances = json_body(status, body, f"Listing {entity_az_name} instances")
    return instances[0] if instances else None


def require_expected_model_shape(api_description):
    entities = {entity["azName"]: entity for entity in api_description.get("entities", [])}
    associations = {association["azName"]: association for association in api_description.get("associations", [])}
    for entity_az_name in ["Person", "FamilyUnit", *FAMILY_NODE_ENTITIES, *PERSON_NODE_ENTITIES]:
        if entity_az_name not in entities:
            raise SystemExit(f"Loaded model is missing entity {entity_az_name}")
    for association_az_name in [
        "FamilyUnit_Spouses",
        "FamilyUnit_Children",
        "FamilyUnitNode0_PersonNode1",
        "PersonNode1_FamilyUnitNode2",
        "FamilyUnitNode2_PersonNode3",
        "PersonNode3_FamilyUnitNode4",
        "FamilyUnitNode4_PersonNode5",
        "PersonNode5_FamilyUnitNode6",
        "FamilyUnitNode6_PersonNode7",
    ]:
        if association_az_name not in associations:
            raise SystemExit(f"Loaded model is missing association {association_az_name}")


def ensure_model_loaded():
    status, body = request("GET", f"/data/{quoted(model_az_name)}/_api")
    if status == 200:
        require_expected_model_shape(json.loads(body))
        return
    if status != 404:
        raise SystemExit(f"Checking model {model_az_name} failed with HTTP {status}: {body}")
    with open(vdos_file, "r", encoding="utf-8") as handle:
        script = handle.read()
    status, body = request("POST", "/models/script", script, "text/plain; charset=utf-8")
    if status != 409:
        require_success(status, body, f"Loading model from {vdos_file}")
    status, body = request("GET", f"/data/{quoted(model_az_name)}/_api")
    require_success(status, body, f"Checking loaded model {model_az_name}")
    require_expected_model_shape(json.loads(body))


def ensure_model_instance_root():
    status, body = request("GET", f"/data/{quoted(model_az_name)}/roots")
    roots = json_body(status, body, "Listing model instance roots")
    for root in roots:
        if root.get("visName") == model_instance_root_name:
            return root["instanceRootId"], root.get("visName", model_instance_root_name)
    status, body = request("POST", f"/data/{quoted(model_az_name)}/roots", {"visName": model_instance_root_name})
    created = json_body(status, body, "Creating model instance root")
    return created["instanceRootId"], created.get("visName", model_instance_root_name)


def ensure_entity(entity_az_name, values, identity_keys, action_name):
    filters = {key: values[key] for key in identity_keys}
    existing = find_first_instance(entity_az_name, filters)
    if existing:
        return existing["id"], False
    status, body = request("POST", root_path(f"/{quoted(entity_az_name)}"), values)
    created = json_body(status, body, action_name)
    return created["id"], True


def list_links(association_az_name):
    status, body = request("GET", root_path(f"/_links/{quoted(association_az_name)}"))
    return json_body(status, body, f"Listing {association_az_name} links")


def ensure_link(association_az_name, source_id, target_id):
    links = list_links(association_az_name)
    if any(link.get("sourceInstanceId") == source_id and link.get("targetInstanceId") == target_id for link in links):
        return False
    status, body = request("POST", root_path(f"/_links/{quoted(association_az_name)}"), {"sourceInstanceId": source_id, "targetInstanceId": target_id})
    require_success(status, body, f"Creating {association_az_name} link")
    return True


def person_label(person_key):
    person = person_by_key[person_key]
    birth = person["BirthDate"][:4]
    return f"{person['DisplayName']} ({birth})" if birth else person["DisplayName"]


def family_node_values(family_unit, sort_order):
    return {
        "Label": family_unit["Label"],
        "StartDate": family_unit["StartDate"],
        "EndDate": family_unit["EndDate"],
        "RelationType": family_unit["RelationType"],
        "SortOrder": sort_order,
        "Notes": family_unit["Notes"],
    }


def person_node_values(person_key, role, sort_order, family_key):
    return {
        "Label": person_label(person_key),
        "Role": role,
        "SortOrder": sort_order,
        "Notes": f"Display reference for {person_by_key[person_key]['DisplayName']} in {family_by_key[family_key]['Label']}.",
    }


def create_person_nodes(parent_family_node_id, family_key, person_level_index, ancestors):
    if person_level_index >= len(PERSON_NODE_ENTITIES):
        return
    person_entity = PERSON_NODE_ENTITIES[person_level_index]
    child_family_entity_index = person_level_index + 1
    parent_association = f"{FAMILY_NODE_ENTITIES[person_level_index]}_{person_entity}"
    person_reference_association = f"{person_entity}_Person"
    family_unit = family_by_key[family_key]
    display_rows = [("Spouse", spouse_key) for spouse_key in family_unit["spouses"]]
    display_rows.extend(("Child", child_key) for child_key in family_unit["children"])
    for sort_order, (role, person_key) in enumerate(display_rows, start=1):
        values = person_node_values(person_key, role, sort_order, family_key)
        node_id, created = ensure_entity(person_entity, values, ["Label", "Role", "Notes"], f"Creating {person_entity} {values['Label']}")
        counters["visual_nodes"] += int(created)
        counters["links"] += int(ensure_link(parent_association, parent_family_node_id, node_id))
        counters["links"] += int(ensure_link(person_reference_association, node_id, person_ids[person_key]))
        if role != "Child" or child_family_entity_index >= len(FAMILY_NODE_ENTITIES):
            continue
        for family_sort_order, child_family_key in enumerate(family_units_by_spouse.get(person_key, []), start=1):
            if child_family_key in ancestors:
                continue
            create_family_node(node_id, child_family_key, child_family_entity_index, family_sort_order, [*ancestors, child_family_key])


def create_family_node(parent_person_node_id, family_key, family_level_index, sort_order, ancestors):
    family_entity = FAMILY_NODE_ENTITIES[family_level_index]
    family_unit = family_by_key[family_key]
    values = family_node_values(family_unit, sort_order)
    node_id, created = ensure_entity(family_entity, values, ["Label", "StartDate", "Notes"], f"Creating {family_entity} {family_unit['Label']}")
    counters["visual_nodes"] += int(created)
    counters["links"] += int(ensure_link(f"{family_entity}_FamilyUnit", node_id, family_unit_ids[family_key]))
    if parent_person_node_id is not None:
        parent_person_entity = PERSON_NODE_ENTITIES[family_level_index - 1]
        counters["links"] += int(ensure_link(f"{parent_person_entity}_{family_entity}", parent_person_node_id, node_id))
    create_person_nodes(node_id, family_key, family_level_index, ancestors)


ensure_model_loaded()
instance_root_id, instance_root_name = ensure_model_instance_root()

person_by_key = {person["key"]: person for person in PERSONS}
family_by_key = {family_unit["key"]: family_unit for family_unit in FAMILY_UNITS}
family_units_by_spouse = {}
for family_unit in FAMILY_UNITS:
    for spouse_key in family_unit["spouses"]:
        family_units_by_spouse.setdefault(spouse_key, []).append(family_unit["key"])

person_ids = {}
family_unit_ids = {}
counters = {"persons": 0, "family_units": 0, "visual_nodes": 0, "links": 0}

for person in PERSONS:
    values = {key: person[key] for key in ["GivenNames", "FamilyName", "BirthDate", "DeathDate", "DisplayName", "Notes"]}
    person_id, created = ensure_entity("Person", values, ["GivenNames", "FamilyName", "BirthDate"], f"Creating Person {person['DisplayName']}")
    person_ids[person["key"]] = person_id
    counters["persons"] += int(created)

for family_unit in FAMILY_UNITS:
    values = {key: family_unit[key] for key in ["Label", "StartDate", "EndDate", "RelationType", "Notes"]}
    family_unit_id, created = ensure_entity("FamilyUnit", values, ["Label", "StartDate", "Notes"], f"Creating FamilyUnit {family_unit['Label']}")
    family_unit_ids[family_unit["key"]] = family_unit_id
    counters["family_units"] += int(created)

for family_unit in FAMILY_UNITS:
    family_unit_id = family_unit_ids[family_unit["key"]]
    for spouse_key in family_unit["spouses"]:
        counters["links"] += int(ensure_link("FamilyUnit_Spouses", family_unit_id, person_ids[spouse_key]))
    for child_key in family_unit["children"]:
        counters["links"] += int(ensure_link("FamilyUnit_Children", family_unit_id, person_ids[child_key]))

for sort_order, root_family_key in enumerate(ROOT_FAMILY_UNITS, start=1):
    create_family_node(None, root_family_key, 0, sort_order, [root_family_key])

print(f"Model: {model_az_name}")
print(f"Model instance root id: {instance_root_id}")
print(f"Model instance root name: {instance_root_name}")
print(f"People created: {counters['persons']}")
print(f"Family units created: {counters['family_units']}")
print(f"Visualization nodes created: {counters['visual_nodes']}")
print(f"Association links created: {counters['links']}")
PY
