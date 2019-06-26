
## Dev Quickstart!

Build it!

    cd lookup
    ./gradlew clean build

Load some fatcat lookup data!

    java -jar lookup/build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar fatcat --input data/fatcat_example.json.xz lookup/data/config/config.yml

Run server!

    java -jar lookup/build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar server lookup/data/config/config.yml

Load data with elasticsearch running (docker?)!

    cd matching_fatcat
    npm install
    node main -dump ../data/fatcat_example.json.xz index
    http get localhost:9200/fatcat_glutton/_count

Check it!

    http get localhost:9200/fatcat_glutton
    http get localhost:9200/fatcat_glutton/release/release_dk6ks5mx2naihoskdbamsldqxi

    http get localhost:8080/service/data
    http get "localhost:8080/service/lookup?fatcat=release_hsmo6p4smrganpb3fndaj2lon4"
    http get "localhost:8080/service/lookup?doi=10.1541/ieejsmas.126.158"
    http get "localhost:8080/service/lookup?atitle=Temperature+Dependence+of+Piezoelectric+Properties+of+Sputtered+Aluminum+Nitride+on+Silicon+Substrate&firstAuthor=kano&postValidate=true"

    # with GROBID running connected to this instance
    time http --form --timeout 180 POST localhost:8070/api/processFulltextDocument consolidateHeader=2 consolidateCitations=2 input@Kim2018_Article_OnEllipticGeneraOf6dStringTheo.pdf

    consolidateHeader=0 consolidateCitations=0
    real    0m10.866s
    real    0m11.176s

    consolidateHeader=2 consolidateCitations=0
        => did not match
    real    0m11.521s
    real    0m10.512s

    consolidateHeader=2 consolidateCitations=2
    http: error: Request timed out (180.0s).
    real    3m0.345s

## Plan: Replace Crossref 

Remove some Crossref stuff and replace with fatcat, instead of trying to
support both as full metadata lookup back-ends.

Matching part pretty easy: add fatcat_ident field to elasticsearch; keep DOI field.

Lookup metadata db is by release ident as key, with fatcat entity JSON as main
body.

For direct DOI lookups, add a DOI-to-identifiers table. Hit this first, then do
a lookup against fatcat db. Or just fail? Blech.

## GROBID Changes

GROBID also needs changes. Needs to inject new identifiers (fatcat, arxiv,
wikidata_qid), but also needs to parse fatcat schema (responses from glutton).

Could extend 'glutton' backend, but think I will add new 'glutton_fatcat'
backend instead.

## Production Commands

Patched to add zlib support. Blech.

    zcat /srv/biblio-glutton/datasets/release_export_expanded.json.gz | node main index

    [...]
    Loaded 2131000 records in 296.938 s (8547.008547008547 record/s)
    Loaded 2132000 records in 297.213 s (7142.857142857142 record/s)
    Loaded 2133000 records in 297.219 s (3816.793893129771 record/s)
    Loaded 2134000 records in 297.265 s (12987.012987012988 record/s)
    Loaded 2135000 records in 297.364 s (13513.513513513513 record/s)
    [...]
    Loaded 98076000 records in 22536.231 s (9433.962264150943 record/s)
    Loaded 98077000 records in 22536.495 s (9090.90909090909 record/s)
    Error occurred: TypeError: Cannot read property 'container_name' of undefined

I think that last one is basically spurious? Though did result in last ~1000
not getting inserted.

    # as grobid user
    java -jar lookup/build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar fatcat --input /srv/biblio-glutton/datasets/release_export_expanded.json.gz /srv/biblio-glutton/config/biblio-glutton.yaml

    [...]
    -- Meters ----------------------------------------------------------------------
    fatcatLookup
                 count = 1146817
             mean rate = 8529.29 events/second
         1-minute rate = 8165.53 events/second
         5-minute rate = 6900.17 events/second
        15-minute rate = 6358.60 events/second
    [...]

    6/26/19 4:32:11 PM =============================================================

    -- Meters ----------------------------------------------------------------------
    fatcatLookup
                 count = 37252787
             mean rate = 1474.81 events/second
         1-minute rate = 1022.73 events/second
         5-minute rate = 1022.72 events/second
        15-minute rate = 1005.36 events/second
    [...]

    cut off around this point to experiment

When debugging, can run directly:

    java -jar lookup/build/libs/lookup-service-1.0-SNAPSHOT-onejar.jar server /srv/biblio-glutton/config/biblio-glutton.yaml

and GROBID:

    sudo su grobid
    cd /srv/grobid/grobid-fatcat
    export TMPDIR=/run/grobid/tmp/
    ./gradlew run --gradle-user-home .


## Issues To Open

GROBID: glutton hang results in GROBID hang

    Should be a timeout instead, and return a 500 error? A reverse proxy (like
    nginx or HAproxy) around the glutton service would probably prevent this.

GROBID: half-baked idea: include hash and file size in TEI-XML output

    Conceptually, about keeping track of document pipeline ("where did this
    derived document come from")? Would make debugging easier in some cases,
    and could make some validation pipelines easier.

    On the other hand, might not be worth the development time, code
    complexity, or CPU time. Would certainly want it to be configurable. I
    would propose supporting SHA-1 to start, and possibly support SHA-256.

GROBID PR: WIP: support for glutton_fatcat remote

    This probably won't be merged as-is until there is some consensus on the
    right way to do things on the biblio-glutton side, but i'm posting here for
    visibility and feedback.

    These GROBID-side changes were pretty easy to integrate, I basically just
    added a new consolidation enum type, and a parser class that inherits from
    the existing Crossref Work parser. I also added 'fatcat' and 'wikidata'
    identifier types and insert those into TEI-XML output.

biblio-glutton: slow loading?

    started at ~8000 docs/sec, slowed to ~900 docs/sec after 33 million or so.
    Is that expected? Seemed to mostly be reads. Notably, elasticsearch ingest
    completed much faster.

## Old Debug

This query way looping forever; seems to be the 'biblio' part:

    http get 'localhost:8080/service/lookup?doi=10.1016%2FS0550-3213%2800%2900148-6&volume=581&jtitle=Nucl.+Phys.+B&firstPage=257&parseReference=false&biblio=K.A.+Intriligator%2C+Anomaly+matching+and+a+Hopf-Wess-Zumino+term+in+6d%2C+N+%3D+%282%2C+0%29+field+%0Atheories%2C+Nucl.+Phys.+B+581+%282000%29+257+%5Bhep-th%2F0001205%5D+%5BINSPIRE%5D.&atitle=Anomaly+matching+and+a+Hopf-Wess-Zumino+term+in+6d%2C+N+%3D+%282%2C+0%29+field+theories&firstAuthor=Intriligator'

Introduced by my changes to biblio-glutton; doesn't happen in `master` branch. Does happen locally.

Fixed due to some callback not getting called.

