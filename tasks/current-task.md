# Current Task

## Luo Metsäpalsta Vedenemo-malli vdos-tiedosto

Status: executed.

### Goal

Tämä suunnitelma on poikkeuksellisesti suomeksi, koska aihepiiri ja tietomalli
ovat suomeksi ja suomalaisessa kontekstissa.

Luo uusi `.vedenemo/Metsapalsta.vdos`-mallitiedosto, jonka juuri-entityn
`visName` on `Metsäpalsta`. Ennen tiedoston luontia lisää Vedenemoon
attribuuttien pakollisuusmetadata, jotta malli voi ilmaista, mitkä attribuutit
ovat pakollisia ja mitkä optionaalisia. Tämä metadata voi myöhemmin ohjata UX:n
lomakkeita ja validointia.

`visName` -> `azName` -muunnossääntö tätä mallia varten:

- ASCII-kirjaimet `a-zA-Z` säilyvät ennallaan.
- `ä` korvataan `a`-kirjaimella.
- `ö` korvataan `o`-kirjaimella.
- Muut merkit, jotka eivät kelpaa `azName`-nimeen, korvataan alaviivalla.
- Suunnitelmassa `NUMBER` on korjattu Vedenemon toteutettua tyyppiä vastaavaksi
  muodoksi `NUMERIC`.

### Model

Mallin tekninen nimi on `Metsapalsta` ja näkyvä nimi on `Metsäpalsta`.

#### Metsäpalsta

Attribuutit:

- `nimi` / `nimi`: `TEXT`, pakollinen.
- `tunnus` / `tunnus`: `TEXT`, optionaalinen.
- `alue` / `alue`: `LOCATION_AREA`, optionaalinen.
- `hehtaarit` / `hehtaarit`: `NUMERIC`, optionaalinen.

Assosiaatiot:

- `Metsapalsta_koostuu_Metsakuvio`, `visName="koostuu"`, `OWNERSHIP`,
  lähde `Metsapalsta`, kohde `Metsakuvio`, kardinaliteetti `1..*`.

#### Metsäkuvio

Attribuutit:

- `tunnus` / `tunnus`: `TEXT`, pakollinen.
- `alue` / `alue`: `LOCATION_AREA`, optionaalinen.
- `hehtaarit` / `hehtaarit`: `NUMERIC`, optionaalinen.

Assosiaatiot:

- `Metsakuvio_sisaltaa_Puulaji`, `visName="sisältää"`, `REFERENCE`,
  lähde `Metsakuvio`, kohde `Puulaji`, kardinaliteetti `0..*`.

#### Puulaji

Attribuutit:

- `nimi` / `nimi`: `TEXT`, pakollinen, `valueSet=PuulajiNimi`.

ValueSet:

- `PuulajiNimi`: `TEXT`
- `MANTY` / `Mänty`
- `KOIVU` / `Koivu`
- `KUUSI` / `Kuusi`
- `LEPPA` / `Leppä`

Assosiaatiot:

- `Puulaji_on_otoksia_Mittaus`, `visName="on otoksia"`, `REFERENCE`,
  lähde `Puulaji`, kohde `Mittaus`, kardinaliteetti `0..*`.

#### Mittaus

Attribuutit:

- `halkaisija_cm` / `halkaisija_cm`: `NUMERIC`, pakollinen.
- `mittauskorkeus_m` / `mittauskorkeus_m`: `NUMERIC`, optionaalinen.
- `lokaatio` / `lokaatio`: `LOCATION`, optionaalinen.

### Implementation Plan

1. Lisää attribuuttimalliin pakollisuusmetadata pure-JDK
   `vedenemo-model-api` -tasolla.
2. Laajenna `CreateAttributeCommand`, `CommandExecutor`, undo-polku ja
   `VedenemoScriptService` säilyttämään pakollisuus `.vdos`-komennoissa ja
   snapshot-riveillä.
3. Laajenna HTTP DTO:t, mallilistauksen vastaukset, selainkonsolin
   in-process-komentopolku ja terminaali-CLI:n HTTP-komentopolku välittämään
   pakollisuusmetadata.
4. Laajenna CLI- ja selainkonsolipromptit kysymään attribuutin pakollisuus
   konservatiivisella oletuksella `false`, jotta vanha authoring-polku pysyy
   kevyenä.
5. Laajenna mallikuluttajien kuvaukset ja frontendin tyyppikäsittely näyttämään
   tai välittämään pakollisuusmetadata ilman instanssidatan validointimuutosta
   tässä tehtävässä.
6. Lisää `.vedenemo/Metsapalsta.vdos` käyttäen yllä määriteltyjä entiteettejä,
   attribuutteja, `PuulajiNimi`-ValueSetiä ja assosiaatioita.
7. Päivitä käyttäjädokumentaatio konkreettisesti toteutuneista muutoksista:
   `README.md`, `docs/cli-reference.md`, `docs/architecture_doc.md` ja
   tarvittaessa `.vdos`-esimerkit.

### Scope

- Attribuutin pakollisuusmetadata mallissa, komennoissa, HTTP-vastauksissa,
  CLI:ssä, selainkonsolissa, `.vdos`-tuonnissa ja `.vdos`-viennissä.
- `Metsapalsta.vdos`-malli yllä olevalla rakenteella.
- Taaksepäin yhteensopiva `.vdos`-tuonti vanhoille attribuuttiriveille, joissa
  pakollisuuskenttä puuttuu; puuttuva arvo tulkitaan optionaaliseksi.
- Testit pakollisuusmetadatan säilymiselle ja `Metsapalsta.vdos`-tuonnille.

### Out Of Scope

- Instanssidatan create/update-validointi pakollisten attribuuttien perusteella.
- `.vdmp`-tuonnin pakollisuusvalidointi.
- Pakollisuuden muuttaminen jälkikäteen erillisellä komennolla.
- Attribuuttien oletusarvot.
- Uudet kardinaliteettisemantiikat assosiaatioille.
- Map- tai paikkatietovisualisointi `LOCATION` / `LOCATION_AREA` -arvoille.

### Acceptance Criteria

- `VAttribute` tai vastaava mallityyppi erottaa pakolliset ja optionaaliset
  attribuutit.
- Uusi attribuutti on oletuksena optionaalinen, jos kutsuja ei anna
  pakollisuustietoa.
- Pakollisuustieto säilyy `CreateAttributeCommand`-komennossa, command
  journalissa, `.vdos`-viennissä ja `.vdos`-tuonnissa.
- Vanhojen `.vdos`-tiedostojen attribuuttirivit ilman pakollisuuskenttää
  tuodaan edelleen onnistuneesti.
- HTTP model/session -rajapinnat palauttavat attribuuttien pakollisuustiedon.
- CLI ja selainkonsoli pystyvät luomaan sekä pakollisia että optionaalisia
  attribuutteja.
- `.vedenemo/Metsapalsta.vdos` löytyy repositoriosta.
- `Metsapalsta.vdos` importoituu onnistuneesti paikalliseen backend-prosessiin.
- `Metsapalsta`-mallissa on entiteetit `Metsapalsta`, `Metsakuvio`, `Puulaji`
  ja `Mittaus`.
- `Metsapalsta`-mallin NUMERIC-, TEXT-, LOCATION- ja LOCATION_AREA-attribuutit
  vastaavat suunnitelmaa.
- `Puulaji.nimi` viittaa `PuulajiNimi`-ValueSetiin, jonka arvot ovat `MANTY`,
  `KOIVU`, `KUUSI` ja `LEPPA`.
- `Metsapalsta_koostuu_Metsakuvio` on `OWNERSHIP`-assosiaatio
  kardinaliteetilla `1..*`.
- `Metsakuvio_sisaltaa_Puulaji` ja `Puulaji_on_otoksia_Mittaus` ovat
  `REFERENCE`-assosiaatioita kardinaliteetilla `0..*`.
- `mvn clean verify` onnistuu.

### Completion Notes

- Lisätty pure-JDK `required`-metadata `VAttribute`-mallityyppiin.
- Laajennettu `CreateAttributeCommand`, `CommandExecutor`, command journalin
  säilyttämä komento, `.vdos`-vienti ja `.vdos`-tuonti säilyttämään attribuutin
  pakollisuus.
- Vanhojen `.vdos`-attribuuttirivien puuttuva `required`-kenttä tulkitaan
  optionaaliseksi.
- Laajennettu HTTP session/model/API-description -vastaukset, terminaali-CLI,
  selainkonsoli ja frontendin tyyppipinta välittämään pakollisuusmetadata.
- CLI- ja selainkonsolipromptit kysyvät `Required? [n]:`; tyhjä vastaus luo
  optionaalisen attribuutin.
- Lisätty `.vedenemo/Metsapalsta.vdos`, jossa on `Metsapalsta`, `Metsakuvio`,
  `Puulaji` ja `Mittaus`, `PuulajiNimi`-ValueSet, yksi `OWNERSHIP`-assosiaatio
  ja kaksi `REFERENCE`-assosiaatiota.
- Päivitetty README, CLI reference ja nykyisen toteutuksen arkkitehtuuridoc.
- Instanssidatan create/update-validointi pakollisten attribuuttien perusteella
  jäi tarkoituksella tämän tehtävän ulkopuolelle.
- Verifiointi: `mvn -q clean verify` ja `cd vedenemo-ux && npm run build`.
