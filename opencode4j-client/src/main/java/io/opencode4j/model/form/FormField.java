package io.opencode4j.model.form;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

/**
 * A typed field requested by an OpenCode integration form.
 *
 * <p>Unrecognised {@code type} values deserialize to {@link UnknownField} so newer servers
 * never break older clients.</p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type",
        visible = true,
        defaultImpl = UnknownField.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StringField.class, name = "string"),
        @JsonSubTypes.Type(value = NumberField.class, name = "number"),
        @JsonSubTypes.Type(value = IntegerField.class, name = "integer"),
        @JsonSubTypes.Type(value = BooleanField.class, name = "boolean"),
        @JsonSubTypes.Type(value = MultiselectField.class, name = "multiselect"),
        @JsonSubTypes.Type(value = ExternalField.class, name = "external")
})
public sealed interface FormField
        permits StringField, NumberField, IntegerField, BooleanField,
        MultiselectField, ExternalField, UnknownField {

    String key();

    String title();

    String description();

    boolean required();

    boolean hidden();

    List<FormWhen> when();
}
