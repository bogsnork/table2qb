# Using table2qb

* [Running and getting help](#running-and-getting-help)
* [Creating components](#creating-components)
* [Creating codelists](#creating-codelists)
* [Creating cubes](#creating-cubes)
    * [Measure-dimension cubes](#measure-dimension-cubes)
    * [Running the cube pipeline](#running-the-cube-pipeline)
* [URIs](#uris)
    * [URI templates](#uri-templates)
    * [Transforms](#transforms)
* [Things to check](#things-to-check)
* [Validation](#validation)

`table2qb` is a utility for specifying and generating elements of an [RDF Data Cube](https://www.w3.org/TR/vocab-data-cube/). A data cube contains
a collection of homogeneous statistical observations along with a definition of their structure. Each observation is identified by a collection of
_dimension_ values corresponding to one or more observed _measure_ values along with an optional set of _attributes_ which allow further interpretation 
of the observed value(s). `table2qb` exposes a number of 'pipelines' which generate the various elements that comprise a cube. A pipeline is a command
which takes a number of named arguments and outputs RDF data either to a file or to the standard output stream. This RDF data can then be inserted into
an RDF data store for further processing.

## Running and getting help

Once installed, `table2qb` can be run with the `table2qb` command:

    table2qb
    
Running without any further arguments outputs a brief help message which describes the tasks that `table2qb` provides. A task is a sub-command
exposing some functionality with its own arguments. For example the `help` task displays the help for a particular task e.g.

    table2qb help list
    
describes how to use the `list` task.

## Creating components

An observation consists of a number of dimensions, one or more measures and an optional set of attributes - collectively these are referred to
as _components_. Before being referenced by an observation, these components must be defined. Some components you wish to reference 
(e.g. [sdmx-dimension:refArea](http://purl.org/linked-data/sdmx/2009/dimension#refArea)) may already be defined by existing vocabularies, but
you may have additional components specific to your organisation you wish to define. These can be created using the `components-pipeline`.
Components are defined by the rows of a CSV file containing the following columns:

* `Label`: The name of the component. Note that various properties of the generated component are derived from this field (described below).
* `Description`: Textual description of the component.
* `Component Type`: One of `Dimension`, `Measure` or `Attribute` which specifies which component type is being defined.
* `Codelist`: An optional URI for the corresponding code list. A code list enumerates the possible value a component can take within an observation and can be generated by the [codelist-pipeline](#creating-codelists).

The [employment example](../examples/employment/README.md) contains a [component definitions file](../examples/employment/csv/components.csv) defining two components: a gender
dimension and a count measure. The `components-pipeline` is run by providing the components file along with a base URI used to construct the URIs for the generated components
and their properties. This will usually be some sub-path of the linked data domain you will be hosting your cubes under. The `components-pipeline` is run using the `exec` task:

    table2qb exec components-pipeline --input-csv path/to/components.csv --base-uri http://example.com/ --output-file components.ttl
    
Running with the employment example components results in two components being defined in the output `components.ttl` file. Within `components.ttl`
the `Gender` dimension is defined with the following properties:

```turtle
<http://example.com/def/dimension/gender> rdfs:label "Gender" .
<http://example.com/def/dimension/gender> dcterms:description "The state of being male or female" .
<http://example.com/def/dimension/gender> a qb:DimensionProperty .
<http://example.com/def/dimension/gender> qb:codeList <http://statistics.gov.scot/def/concept-scheme/gender> .
<http://example.com/def/dimension/gender> skos:notation "gender" .
<http://example.com/def/dimension/gender> rdfs:range <http://example.com/def/Gender> .
<http://example.com/def/dimension/gender> rdfs:isDefinedBy <http://example.com/def/ontology/components> .
<http://example.com/def/dimension/gender> a rdf:Property .
```

There are a few things to note about the resulting component:

* The component URI is derived from the component `Label` and the provided `base-uri` value. The label is converted into a [slug](#slugizing) and then combined
  with the base URI and component type (dimension, measure or attribute) as `{base-uri}/def/{component-type}/{slugged-label}`. For example, the gender dimension has
  a URI of `http://example.com/def/dimension/gender`, the count measure `http://example.com/def/measure/count`, a `Trade Currency` attribute would be
  `http://example.com/def/attribute/trade-currency` etc.
* An `rdf:type` property is defined as `qb:DimensionProperty`, `qb:MeasureProperty` or `qb:AttributeProperty` depending on whether the `Component Type` is
  `Dimension`, `Measure` or `Attribute` respectively.  
* The value of the `skos:notation` property is the slugized version of the label.
* The value of the `rdfs:range` property is derived from the base URI and the [classized](#classizing) version of the label.
  The resulting value URI is `{base-uri}/def/{classized-label}`.

The `Measure` component is defined similarly except its type is `qb:MeasureProperty` and it has no associated `qb:codeList` property.

The URI structure of some component properties will be made more configurable in a future version.

## Creating codelists

The values of certain dimension or attribute components within a data cube may be enumerated in a set of values. For such
components, a _code list_ should be defined containing the possible values. In line with the recommendations within the
[RDF data cube specification](https://www.w3.org/TR/vocab-data-cube/), `table2qb` generates a `skos:ConceptScheme` from a CSV
file defining the values and (where present) the hierarchical structure of the codelist. The definition CSV file should contain the following columns:

* `Label`: Label for the concept.
* `Notation`: A unique value used to identify the code. The notation is used to generate the corresponding concept URI so it should
  only contain URI-compatible characters. A common option is to make a slug of the label,  e.g. `3 Mineral Fuels` becomes `3-mineral-fuels`, or to use a pre-existing set of alpha-numeric codes.
* `Parent Notation`: Codelists can be hierarchical where one entry represents a specialisation of another entry in the list. The parent notation
  should contain the notation of the broader concept in the list if one exists.
* `Description`: Textual description of the concept.
* `Sort Priority`: Optional numeric value indicating the position of the value within the code list. Some user interfaces may use this value
  to sort the code list values for display purposes.
  
The [employment example](../examples/employment/README.md) contains a [gender codelist](../examples/employment/csv/gender.csv). Note that the optional `Description` and `Sort Priority` columns
are missing. This file can be used to generate the codelist with `codelist-pipeline`:

    table2qb exec codelist-pipeline --codelist-csv path/to/gender-codelist.csv --codelist-name Gender --codelist-slug gender --base-uri http://example.com/ --output-file gender.ttl
    
This generates a `skos:ConceptScheme` for gender containing members for the `All`, `Female` and `Male` members within the codelist CSV file. The codelist is defined as:

```turtle
<http://example.com/def/concept-scheme/gender> dcterms:title "Gender"@en ;
        rdfs:label "Gender"@en ;
        a skos:ConceptScheme ;
```

The URI of the codelist is constructed from the `base-uri` and `codelist-slug` parameters provided to the `codelist-pipeline` invocation above. The constructed URI has the
form `{base-uri}/def/concept-scheme/{codelist-slug}`. Note that when defining components using `components-pipeline`, any associated codelist URI for the component must match
the one generated by `codelist-pipeline` (or the URI of another Concept Scheme).

The members of the concept scheme have URIs of the form `{base-uri}/def/concept-scheme/{codelist-slug}/{notation}` where `notation` is the corresponding value of the `Notation`
column within the codelist CSV file. Member URIs have a prefix of the containing codelist URI. Along with some additional properties, the `Female` member of the `Gender` codelist is defined as:

```turtle
<http://example.com/def/concept/gender/female> rdfs:label "Female" .
<http://example.com/def/concept/gender/female> skos:broader <http://example.com/def/concept/gender/all> .
<http://example.com/def/concept/gender/female> skos:inScheme <http://example.com/def/concept-scheme/gender> .
<http://example.com/def/concept-scheme/gender> skos:member <http://example.com/def/concept/gender/female> .
```

The member is connected to its containing scheme through the `skos:inScheme` and `skos:member` properties. Since the `Female` member has a `Parent Notation` of `all` it has a 
`skos:broader` relationship with the corresponding `all` member within the scheme.

## Creating cubes

After defining components and any associated codelists, data cubes can be created by the `cube-pipeline` given a file containing observation data. The observation table should be arranged 
in [tidy data format](http://vita.had.co.nz/papers/tidy-data.pdf) i.e. one row per observation with one column per component (dimension, attribute or measure). An example observation file can 
be seen in the [employment example](../examples/employment/csv/input.csv). Along with the observations data, the `cube-pipeline` requires a configuration file which describes the meaning of each
column in the data and how to process the cells within. This configuration file should contain the following columns:

* `title`: Identifies a column heading within the observations data. This must be unique, each row of the configuration file (and hence each column of an observations file) must have a different title.
* `name`: The variable name by which the column may be refered to within URI templates. It is recommended this value is the lower-cased version of the `title` with spaces replaced with underscores e.g. the name of the `Measure Type` column would be `measure_type`. Names should be unique within the configuration. Hyphens aren't permitted in URI templates.
* `component_attachment`: Either blank, or one of `qb:dimension`, `qb:measure` or `qb:attribute` to indicate whether the column defines a dimension, attribute or measure of the observations. If blank, the column is assumed to contain observation values and will be attached to observations using the relevant measure property (see [Measure dimension cubes](#measure-dimension-cubes) for more details).
* `property_template`: template for building the component property URIs used to link the corresponding value to the observation.
* `value_template`: Optional URI template for component values.
* `datatype`: Datatype of the values within the corresponding column.
* `value_transformation`: Optional transformation to apply to cell values in the corresponding column. If specified it should be either [slugize](#slugize) or [unitize](#unitize). More transformations may be offered in future.

An example column configuration file is defined in the [employment example](../examples/employment/columns.csv). The configuration must define all columns to be used within an observations file, but can also contain definitions
for additional columns that do not exist. This means that all known columns for multiple different cubes can be defined within a single definition file (subject to the constraints that the values within the `title` and `name` columns must be unique).

### Cube types

Observations within a cube are distinguished by the set of dimension values, but may have multiple associated measures. The [data cube specification](https://www.w3.org/TR/vocab-data-cube/#dsd-mm) suggests two 
approaches to handling multiple measures. One is to associate a single measure value with each observation and to include an explicit "measure type" dimension which indicates which measure is being used. Such 
cubes are henceforth referred to as ["measure dimension" cubes](#measure-dimension-cubes). The other approach ("multi-measure" cubes) associates a value for each measure to each observation. `table2qb` currently
only supports the "measure dimension" approach but support for multi-measure cubes [is planned for a future release](https://github.com/Swirrl/table2qb/issues/23).

### Measure dimension cubes

A 'measure dimension' cube is one where each observation has a single measure and a `qb:measureType` dimension indicating which measure the observation corresponds to. If the cube contains multiple measures this means there
should be multiple observations for each combination of dimension values in the observations data (note `table2qb` does validate this requirement, see [validation](#validation) for validating generated cubes).
`table2qb` requires the following constraints are met by the observations data:

* A single column exists with a `property_template` of `http://purl.org/linked-data/cube#measureType`. Note this is the literal value which must be used; the compact form of `qb:measureType` is not accepted.
* Each cell in the measure type column must identify the associated measure by its `title`. The identified column must have a `component_attachment` of `qb:measure`.
* A single value column must exist. A `value` column is one with an empty `component_attachment` within the columns configuration. This should contain the value for the associated measure type.

The [employment example observation file](../examples/employment/input.csv) defines a measure dimension cube where:

* The `Measure Type` column has a `property_template` of `http://purl.org/linked-data/cube#measureType`.
* The values in the `Measure Type` column reference the corresponding `qb:measure` column corresponding to the measure (by its `title` property). There is only a single measure used in the cube (i.e. the `Count` measure).
* The `Value` column contains the measure value. The configuration for this column has an empty `component_attachment`.

### Running the cube-pipeline

Given an observations file and a columns configuration file, the `cube-pipeline` can be run:

    table2qb exec cube-pipeline --input-csv path/to/input.csv --dataset-name Dataset --dataset-slug dataset --column-config path/to/column-configuration.csv --base-uri http://example.com/ --output-file cube.ttl
    
The URI of the generated cube will have the form `{base-uri}/data/{dataset-slug}` where `dataset-slug` is the value of the parameter provided `cube-pipeline`. The cube will have a title matching the `dataset-name` parameter.
A `qb:Observation` is generated for each row in the observations CSV data. The observation corresponding to the first row of the observations within the [employment example](../examples/employment/csv/input.csv) is:

```turtle
<http://example.com/data/employment/S12000039/2017-Q1/female/count> a qb:Observation .
<http://example.com/data/employment/S12000039/2017-Q1/female/count> qb:dataSet <http://example.com/data/employment> .
<http://example.com/data/employment/S12000039/2017-Q1/female/count> sdmx-dimension:refArea <http://statistics.data.gov.uk/id/statistical-geography/S12000039> .
<http://example.com/data/employment/S12000039/2017-Q1/female/count> sdmx-dimension:refPeriod <http://reference.data.gov.uk/id/quarter/2017-Q1> .
<http://example.com/data/employment/S12000039/2017-Q1/female/count> <http://statistics.gov.scot/def/dimension/gender> <http://statistics.gov.scot/def/concept/gender/female> .
<http://example.com/data/employment/S12000039/2017-Q1/female/count> qb:measureType <http://statistics.gov.scot/def/measure/count> .
<http://example.com/data/employment/S12000039/2017-Q1/female/count> sdmx-attribute:unitMeasure <http://statistics.gov.scot/def/concept/measure-units/people> .
<http://example.com/data/employment/S12000039/2017-Q1/female/count> <http://statistics.gov.scot/def/measure/count> 2.07E4 .
```

The URI of the observation is derived from the `base-uri` parameter and the dimension and measure values for the observation. The properties linking the observation to the corresponding component values are constructed from
the `property_template` URI template in the column specification file. Similarly the corresponding values are either literals based on the declared data type, or URIs derived from the `value_template` column. To illustrate,
the value of the `Gender` column for this observation in the observation data is `Female`. The column specification for the `Gender` column defines a `property_template` of `http://statistics.gov.scot/def/dimension/gender` and
a `value_template` of `http://statistics.gov.scot/def/concept/gender/{gender}`. Note the `gender` column is referenced by its `name` (not `title`) within the value URI template. The column also defines a `value_transformation`
of `slugize` so observation values are converted into URI slugs before being incorporated into the value template. These are combined to produce the statement

```turtle
<http://example.com/data/employment/S12000039/2017-Q1/female/count> <http://statistics.gov.scot/def/dimension/gender> <http://statistics.gov.scot/def/concept/gender/female> .
```

shown above. If a codelist has been generated by the `codelist-pipeline`, care must be taken to ensure the `value_template` for the associated dimension matches the format of the URI for generated members. 

## URIs

`table2qb` pipelines output RDF data which frequently uses URIs to identify resources. `table2qb` allows some customisation in the way URIs are generated through URI templates and various transformation on
the input data. These are described below.

### URI Templates

`table2qb` allows some URIs to be parameterised by input data, such as the `property_template` and `value_template` of the data cube columns configuration file.
The format of these templates are defined by [RFC 6570 - URI Templates](https://tools.ietf.org/html/rfc6570) however the majority of templates will use relatively basic
features. The most common usage is to parameterise URIs by the values of various columns e.g.

    http://example/def/concept/gender/{gender}
    
This references the `gender` column, which should be defined within the columns configuration. Note referencing columns within URI templates is done by the column `name`
and not its `title` (i.e. `gender` instead of `Gender`).

### Transforms

URIs are frequently used to identify linked data resources and `table2qb` generates URIs in various places for this purpose. URIs place restrictions on the permissible characters within each component, and users
have additional expectations around the conventions used when building URIs. Since URIs components can be constructed from free-form text, `table2qb` applies various transforms to the input data before incorporating
them into URI templates.

#### Slugize

Input text is converted into a 'slugged' version using the `slugize` transformation. This transformation is defined as:

1. Convert the input string to lower-case
1. Replace any non alphabetical characters with a `-` character
1. Replace sequences of `-` with a single `-` character
1. Remove any trailing `-`

For example "Gender" will be converted to `gender`, "Export and Import Activity" to `export-and-import-activity`.

#### Unitize

The `unitize` transformation is defined as:

1. Replace `£` characters with `GBP`
1. Follow the `slugize` transformation

For example the text `£ 10000` is converted into `gbp-10000`.

#### Classize

The `classize` transformation is defined as:

1. Capitalise the first letter of each word
2. Remove whitespace around words

For example the text "date of birth" is converted into `DateOfBirth`.

Note this transformation is only used internally for generating some URIs and is not a valid value for the `value_transformation` in the data cube columns configuration.

## Things to check

The component, codelist and cube pipelines are run independently but resources created in one may be referenced in another.
Below are some areas where care should be taken to ensure URIs generated by one pipeline match those referenced by another.

1. If a component codelist is generated by the codelist pipeline, the corresponding codelist URI in the input provided
   to the codelist pipeline should match. By default codelist URIs generated by the codelist pipeline have the form
   `{base-uri}/def/concept-scheme/{codelist-slug}` where `codelist-slug` is a parameter to the codelist pipeline.
2. Within a columns configuration file provided to the cube pipeline, components are referenced via the `property_template`
   column. If a column refers to a component generated by the `components-pipeline` the component will by default have a URI
   of the form `{base-uri}/def/(dimension|measure|attribute)/{component-slug}` where `component-slug` is the [slugized](#slugize)
   version of the component label.
3. URI values for observation dimension and measures are specified by the `value_template` of the column within `column-config`
   provided to the `cube-pipeline`. If the values should exist in a codelist which was generated by the `codelist-pipline` then
   by default the `value_template` should have the form `{base-uri}/def/concept/{codelist-slug}{column_name}` where `codelist-slug`
   was the slug provided to the `codelist-pipeline` and `column_name` is the `name` specified for the column in the
   columns configuration (i.e. the value in the `name` column in the corresponding row in `columns.csv`). The values for the
   column in the observations CSV file should match the `notation` of the corresponding codelist entry defined in the 
   codelist definition provided to the `codelist-pipeline`. It may be required to apply the `slugize` transformation to cell
   values to match them to the defined notation.

## Validation

The pipelines used to define data cubes are run independently and `table2qb` makes no attempt to validate that the various elements are defined and referenced consistently. For example, users
must ensure that the codelist URI for a component matches the one generated for the concept scheme created by the `codelist-pipeline`. It is therefore recommended that data cubes are validated
for consistency after they have been generated. One such tool for validating RDF data is [rdf-validator](https://github.com/Swirrl/rdf-validator). This supports validating that a generated
cube conforms to the [RDF data cube specification](https://www.w3.org/TR/vocab-data-cube/).
