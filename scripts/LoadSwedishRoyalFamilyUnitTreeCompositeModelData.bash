#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

API_BASE_URL="${VEDENEMO_API_BASE_URL:-http://127.0.0.1:8080}"
MODEL_AZ_NAME="FamilyUnitTreeComposite"
MODEL_INSTANCE_ROOT_NAME="Swedish Royal Family Unit Tree Composite"
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
    {"key": "gustaf_adolf_sibylla", "Label": "Gustaf Adolf + Sibylla", "StartDate": "1932-10-20", "EndDate": "1947-01-26", "RelationType": "Marriage", "Status": "Ended by death", "Notes": "Marriage of Prince Gustaf Adolf and Princess Sibylla.", "partners": ["gustaf_adolf", "sibylla"], "children": ["margaretha", "birgitta", "desiree", "christina", "carl_xvi_gustaf"], "childFamilyUnits": ["carl_silvia"]},
    {"key": "carl_silvia", "Label": "Carl XVI Gustaf + Silvia", "StartDate": "1976-06-19", "EndDate": "", "RelationType": "Marriage", "Status": "Active", "Notes": "Marriage of King Carl XVI Gustaf and Queen Silvia.", "partners": ["carl_xvi_gustaf", "silvia"], "children": ["victoria", "carl_philip", "madeleine"], "childFamilyUnits": ["victoria_daniel", "carl_philip_sofia", "madeleine_christopher"]},
    {"key": "victoria_daniel", "Label": "Victoria + Daniel", "StartDate": "2010-06-19", "EndDate": "", "RelationType": "Marriage", "Status": "Active", "Notes": "Marriage of Crown Princess Victoria and Prince Daniel.", "partners": ["victoria", "daniel"], "children": ["estelle", "oscar"], "childFamilyUnits": []},
    {"key": "carl_philip_sofia", "Label": "Carl Philip + Sofia", "StartDate": "2015-06-13", "EndDate": "", "RelationType": "Marriage", "Status": "Active", "Notes": "Marriage of Prince Carl Philip and Princess Sofia.", "partners": ["carl_philip", "sofia"], "children": ["alexander", "gabriel", "julian", "ines"], "childFamilyUnits": []},
    {"key": "madeleine_christopher", "Label": "Madeleine + Christopher", "StartDate": "2013-06-08", "EndDate": "", "RelationType": "Marriage", "Status": "Active", "Notes": "Marriage of Princess Madeleine and Christopher O'Neill.", "partners": ["madeleine", "christopher"], "children": ["leonore", "nicolas", "adrienne"], "childFamilyUnits": []},
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
