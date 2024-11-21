/**
 * @typedef {{
 *     name: string|null,
 *     fullName: string|null,
 *     type: 'NULL'|'OBJECT'|'ARRAY'|'STRING'|'BOOLEAN'|'INTEGER'|'DECIMAL'|'DATE'|'BYTES',
 *     children: RecordField[]|null,
 *     nullable: boolean,
 *     value: RecordFieldValue|null,
 * }} RecordField
 *
 * @typedef {{
 *     highCardinality: boolean,
 *     tooBig: boolean,
 *     valueSet: any[]|null,
 * }} RecordFieldValue
 *
 * @typedef {{
 *     payloadType: 'UNKNOWN'|'NULL'|'JSON',
 *     size: Object,
 *     headerFields: RecordField[]|null,
 *     jsonFields: RecordField[]|null,
 *     nullable: boolean,
 * }} RecordStructure
 *
 * @typedef {{
 *     name: string,
 *     fullName: string,
 *     type: string,
 *     nullable: boolean|null,
 *     highCardinality: boolean|null,
 *     enumerated: boolean|null,
 *     valueSet: any[]|null,
 * }} RecordStructureField
 */

/**
 * @param {RecordField[]} fields
 * @return {RecordStructureField[]}
 */
function extractFieldsNames(fields) {
    /** @type RecordStructureField[] */
    let result = [];
    fields.forEach(function (field) {
        Array.prototype.push.apply(result, extractFieldNames(field));
    });
    return result;
}

/**
 * @param {RecordField} field
 * @return {RecordStructureField[]}
 */
function extractFieldNames(field) {
    /** @type RecordStructureField[] */
    let result = [];
    if (field.fullName) {
        result.push({
            name: field.name,
            fullName: field.fullName,
            type: field.type,
            nullable: field.nullable,
            highCardinality: (field.value ? field.value.highCardinality : null),
            tooBig: (field.value ? field.value.tooBig : null),
            enumerated: (field.value ? !field.value.highCardinality && !field.value.tooBig : false),
            valueSet: (field.value ? field.value.valueSet : null),
        });
    }
    if (field.children) {
        Array.prototype.push.apply(result, extractFieldsNames(field.children));
    }
    return result;
}

