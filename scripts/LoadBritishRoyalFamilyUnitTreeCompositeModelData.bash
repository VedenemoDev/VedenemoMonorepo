#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

API_BASE_URL="${VEDENEMO_API_BASE_URL:-http://127.0.0.1:8080}"
MODEL_AZ_NAME="FamilyUnitTreeComposite"
MODEL_INSTANCE_ROOT_NAME="British Royal Family Unit Tree Composite"
VDOS_FILE="${REPO_ROOT}/.vedenemo/FamilyUnitTreeComposite.vdos"

python3 - "$API_BASE_URL" "$MODEL_AZ_NAME" "$MODEL_INSTANCE_ROOT_NAME" "$VDOS_FILE" <<'PY'
import json
import sys
import urllib.error
import urllib.parse
import urllib.request

api_base_url, model_az_name, model_instance_root_name, vdos_file = sys.argv[1:]
api_base_url = api_base_url.rstrip("/")

PERSONS = [
    {"key": "george_vi", "GivenNames": "Albert Frederick Arthur George", "FamilyName": "Windsor", "BirthDate": "1895-12-14", "DeathDate": "1952-02-06", "DisplayName": "George VI", "Notes": "King George VI; father of Queen Elizabeth II."},
    {"key": "queen_mother", "GivenNames": "Elizabeth Angela Marguerite", "FamilyName": "Bowes-Lyon", "BirthDate": "1900-08-04", "DeathDate": "2002-03-30", "DisplayName": "Elizabeth Bowes-Lyon", "Notes": "Queen Elizabeth The Queen Mother."},
    {"key": "elizabeth_ii", "GivenNames": "Elizabeth Alexandra Mary", "FamilyName": "Windsor", "BirthDate": "1926-04-21", "DeathDate": "2022-09-08", "DisplayName": "Elizabeth II", "Notes": "Queen Elizabeth II; mother of King Charles III."},
    {"key": "margaret", "GivenNames": "Margaret Rose", "FamilyName": "Windsor", "BirthDate": "1930-08-21", "DeathDate": "2002-02-09", "DisplayName": "Princess Margaret", "Notes": "Princess Margaret, Countess of Snowdon."},
    {"key": "philip", "GivenNames": "Philip", "FamilyName": "Mountbatten", "BirthDate": "1921-06-10", "DeathDate": "2021-04-09", "DisplayName": "Prince Philip", "Notes": "Prince Philip, Duke of Edinburgh."},
    {"key": "charles", "GivenNames": "Charles Philip Arthur George", "FamilyName": "Mountbatten-Windsor", "BirthDate": "1948-11-14", "DeathDate": "", "DisplayName": "Charles III", "Notes": "King Charles III."},
    {"key": "anne", "GivenNames": "Anne Elizabeth Alice Louise", "FamilyName": "Mountbatten-Windsor", "BirthDate": "1950-08-15", "DeathDate": "", "DisplayName": "Princess Anne", "Notes": "The Princess Royal."},
    {"key": "andrew", "GivenNames": "Andrew Albert Christian Edward", "FamilyName": "Mountbatten-Windsor", "BirthDate": "1960-02-19", "DeathDate": "", "DisplayName": "Prince Andrew", "Notes": "Duke of York."},
    {"key": "edward", "GivenNames": "Edward Antony Richard Louis", "FamilyName": "Mountbatten-Windsor", "BirthDate": "1964-03-10", "DeathDate": "", "DisplayName": "Prince Edward", "Notes": "Duke of Edinburgh."},
    {"key": "diana", "GivenNames": "Diana Frances", "FamilyName": "Spencer", "BirthDate": "1961-07-01", "DeathDate": "1997-08-31", "DisplayName": "Diana", "Notes": "Diana, Princess of Wales; first wife of King Charles III."},
    {"key": "camilla", "GivenNames": "Camilla Rosemary", "FamilyName": "Shand", "BirthDate": "1947-07-17", "DeathDate": "", "DisplayName": "Queen Camilla", "Notes": "Queen Camilla; wife of King Charles III."},
    {"key": "william", "GivenNames": "William Arthur Philip Louis", "FamilyName": "Mountbatten-Windsor", "BirthDate": "1982-06-21", "DeathDate": "", "DisplayName": "Prince William", "Notes": "Prince of Wales."},
    {"key": "harry", "GivenNames": "Henry Charles Albert David", "FamilyName": "Mountbatten-Windsor", "BirthDate": "1984-09-15", "DeathDate": "", "DisplayName": "Prince Harry", "Notes": "Duke of Sussex."},
    {"key": "catherine", "GivenNames": "Catherine Elizabeth", "FamilyName": "Middleton", "BirthDate": "1982-01-09", "DeathDate": "", "DisplayName": "Catherine", "Notes": "Princess of Wales; wife of Prince William."},
    {"key": "meghan", "GivenNames": "Rachel Meghan", "FamilyName": "Markle", "BirthDate": "1981-08-04", "DeathDate": "", "DisplayName": "Meghan", "Notes": "Duchess of Sussex; wife of Prince Harry."},
    {"key": "george", "GivenNames": "George Alexander Louis", "FamilyName": "Mountbatten-Windsor", "BirthDate": "2013-07-22", "DeathDate": "", "DisplayName": "Prince George", "Notes": "Child of Prince William and Catherine."},
    {"key": "charlotte", "GivenNames": "Charlotte Elizabeth Diana", "FamilyName": "Mountbatten-Windsor", "BirthDate": "2015-05-02", "DeathDate": "", "DisplayName": "Princess Charlotte", "Notes": "Child of Prince William and Catherine."},
    {"key": "louis", "GivenNames": "Louis Arthur Charles", "FamilyName": "Mountbatten-Windsor", "BirthDate": "2018-04-23", "DeathDate": "", "DisplayName": "Prince Louis", "Notes": "Child of Prince William and Catherine."},
    {"key": "archie", "GivenNames": "Archie Harrison", "FamilyName": "Mountbatten-Windsor", "BirthDate": "2019-05-06", "DeathDate": "", "DisplayName": "Prince Archie", "Notes": "Child of Prince Harry and Meghan."},
    {"key": "lilibet", "GivenNames": "Lilibet Diana", "FamilyName": "Mountbatten-Windsor", "BirthDate": "2021-06-04", "DeathDate": "", "DisplayName": "Princess Lilibet", "Notes": "Child of Prince Harry and Meghan."},
]

FAMILY_UNITS = [
    {"key": "george_vi_queen_mother", "Label": "George VI + Elizabeth Bowes-Lyon", "StartDate": "1923-04-26", "EndDate": "1952-02-06", "RelationType": "Marriage", "Status": "Ended by death", "Notes": "Marriage of King George VI and Elizabeth Bowes-Lyon.", "partners": ["george_vi", "queen_mother"], "children": ["elizabeth_ii", "margaret"], "childFamilyUnits": ["elizabeth_philip"]},
    {"key": "elizabeth_philip", "Label": "Elizabeth II + Philip", "StartDate": "1947-11-20", "EndDate": "2021-04-09", "RelationType": "Marriage", "Status": "Ended by death", "Notes": "Marriage of Queen Elizabeth II and Prince Philip.", "partners": ["elizabeth_ii", "philip"], "children": ["charles", "anne", "andrew", "edward"], "childFamilyUnits": ["charles_diana", "charles_camilla"]},
    {"key": "charles_diana", "Label": "Charles III + Diana", "StartDate": "1981-07-29", "EndDate": "1996-08-28", "RelationType": "Marriage", "Status": "Dissolved", "Notes": "Marriage of King Charles III and Diana, Princess of Wales; dissolved in 1996.", "partners": ["charles", "diana"], "children": ["william", "harry"], "childFamilyUnits": ["william_catherine", "harry_meghan"]},
    {"key": "charles_camilla", "Label": "Charles III + Camilla", "StartDate": "2005-04-09", "EndDate": "", "RelationType": "Marriage", "Status": "Active", "Notes": "Marriage of King Charles III and Queen Camilla.", "partners": ["charles", "camilla"], "children": [], "childFamilyUnits": []},
    {"key": "william_catherine", "Label": "William + Catherine", "StartDate": "2011-04-29", "EndDate": "", "RelationType": "Marriage", "Status": "Active", "Notes": "Marriage of Prince William and Catherine, Princess of Wales.", "partners": ["william", "catherine"], "children": ["george", "charlotte", "louis"], "childFamilyUnits": []},
    {"key": "harry_meghan", "Label": "Harry + Meghan", "StartDate": "2018-05-19", "EndDate": "", "RelationType": "Marriage", "Status": "Active", "Notes": "Marriage of Prince Harry and Meghan, Duchess of Sussex.", "partners": ["harry", "meghan"], "children": ["archie", "lilibet"], "childFamilyUnits": []},
]


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
    expected_attributes = {
        "Person": ["GivenNames", "FamilyName", "BirthDate", "DeathDate", "DisplayName", "Notes"],
        "FamilyUnit": ["Label", "StartDate", "EndDate", "RelationType", "Status", "Notes"],
    }
    for entity_az_name, attribute_az_names in expected_attributes.items():
        entity = entities.get(entity_az_name)
        if not entity:
            raise SystemExit(f"Loaded model is missing entity {entity_az_name}")
        attributes = {attribute["azName"] for attribute in entity.get("attributes", [])}
        for attribute_az_name in attribute_az_names:
            if attribute_az_name not in attributes:
                raise SystemExit(f"Loaded model is missing attribute {entity_az_name}.{attribute_az_name}")
    expected_associations = {
        "FamilyUnit_Partners": ("FamilyUnit", "Person"),
        "FamilyUnit_Children": ("FamilyUnit", "Person"),
        "FamilyUnit_ChildFamilyUnits": ("FamilyUnit", "FamilyUnit"),
    }
    for association_az_name, endpoints in expected_associations.items():
        association = associations.get(association_az_name)
        if not association:
            raise SystemExit(f"Loaded model is missing association {association_az_name}")
        if (association.get("sourceEntityAzName"), association.get("targetEntityAzName")) != endpoints:
            raise SystemExit(f"Loaded model association {association_az_name} has unexpected endpoints")


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


ensure_model_loaded()
instance_root_id, instance_root_name = ensure_model_instance_root()

person_ids = {}
family_unit_ids = {}
created_persons = 0
created_family_units = 0
created_links = 0

for person in PERSONS:
    values = {key: person[key] for key in ["GivenNames", "FamilyName", "BirthDate", "DeathDate", "DisplayName", "Notes"]}
    person_id, created = ensure_entity("Person", values, ["GivenNames", "FamilyName", "BirthDate"], f"Creating Person {person['DisplayName']}")
    person_ids[person["key"]] = person_id
    created_persons += int(created)

for family_unit in FAMILY_UNITS:
    values = {key: family_unit[key] for key in ["Label", "StartDate", "EndDate", "RelationType", "Status", "Notes"]}
    family_unit_id, created = ensure_entity("FamilyUnit", values, ["Label", "StartDate", "Notes"], f"Creating FamilyUnit {family_unit['Label']}")
    family_unit_ids[family_unit["key"]] = family_unit_id
    created_family_units += int(created)

for family_unit in FAMILY_UNITS:
    family_unit_id = family_unit_ids[family_unit["key"]]
    for partner_key in family_unit["partners"]:
        created_links += int(ensure_link("FamilyUnit_Partners", family_unit_id, person_ids[partner_key]))
    for child_key in family_unit["children"]:
        created_links += int(ensure_link("FamilyUnit_Children", family_unit_id, person_ids[child_key]))
    for child_family_unit_key in family_unit["childFamilyUnits"]:
        created_links += int(ensure_link("FamilyUnit_ChildFamilyUnits", family_unit_id, family_unit_ids[child_family_unit_key]))

print(f"Model: {model_az_name}")
print(f"Model instance root id: {instance_root_id}")
print(f"Model instance root name: {instance_root_name}")
print(f"People created: {created_persons}")
print(f"Family units created: {created_family_units}")
print(f"Association links created: {created_links}")
PY
