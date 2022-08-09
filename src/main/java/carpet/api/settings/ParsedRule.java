package carpet.api.settings;

import carpet.utils.Messenger;
import carpet.utils.TranslationKeys;
import carpet.utils.Translations;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ClassUtils;

/**
 * A Carpet rule parsed from a field, with its name, value, and other useful stuff.
 * 
 * It is the default implementation of {@link CarpetRule} used for the fields with the {@link Rule} annotation
 * when being parsed by {@link SettingsManager#parseSettingsClass(Class)}.
 *
 * @param <T> The field's (and rule's) type
 */
final class ParsedRule<T> implements CarpetRule<T> {
    private static final Map<Class<?>, FromStringConverter<?>> CONVERTER_MAP = Map.ofEntries(
            Map.entry(String.class, str -> str),
            Map.entry(Boolean.class, str -> {
                return switch (str) {
                    case "true" -> true;
                    case "false" -> false;
                    default -> throw new InvalidRuleValueException("Invalid boolean value");
                };
            }),
            numericalConverter(Integer.class, Integer::parseInt),
            numericalConverter(Double.class, Double::parseDouble),
            numericalConverter(Long.class, Long::parseLong),
            numericalConverter(Float.class, Float::parseFloat)
        );
    private final Field field;
    private final String name;
    private final List<String> categories;
    private final List<String> suggestions;
    private final boolean isClient;
    private final Class<T> type;
    private final T defaultValue;
    public final String scarpetApp;
    private final FromStringConverter<T> converter;
    private final SettingsManager settingsManager;
    boolean isStrict;
    final List<Validator<T>> validators;
    
    @FunctionalInterface
    interface FromStringConverter<T> {
        T convert(String value) throws InvalidRuleValueException;
    }

    static <T> ParsedRule<T> of(Field field, SettingsManager settingsManager) {
        Rule rule = field.getAnnotation(Rule.class);
        return new ParsedRule<>(field, rule, settingsManager);
    }

    private ParsedRule(Field field, Rule rule, SettingsManager settingsManager)
    {
        this.name = field.getName();
        this.field = field;
        @SuppressWarnings("unchecked") // We are "defining" T here
        Class<T> type = (Class<T>)ClassUtils.primitiveToWrapper(field.getType());
        this.type = type;
        this.isStrict = rule.strict();
        this.categories = List.of(rule.categories());
        this.scarpetApp = rule.appSource();
        this.settingsManager = settingsManager;
        this.validators = Stream.of(rule.validators()).map(this::instantiateValidator).collect(Collectors.toList());
        this.defaultValue = value();
        FromStringConverter<T> converter0 = null;
        
        if (categories.contains(RuleCategory.COMMAND))
        {
            this.validators.add(new Validators.Implementation.Command<T>());
            if (this.type == String.class)
            {
                this.validators.add(instantiateValidator(Validators.CommandLevel.class));
            }
        }
        
        this.isClient = categories.contains(RuleCategory.CLIENT);
        if (this.isClient)
        {
            this.validators.add(new Validators.Implementation.Client<>());
        }
        
        if (rule.options().length > 0)
        {
            this.suggestions = List.of(rule.options());
        }
        else if (this.type == Boolean.class) {
            this.suggestions = List.of("true", "false");
        }
        else if (this.type == String.class && categories.contains(RuleCategory.COMMAND))
        {
            this.suggestions = Validators.CommandLevel.OPTIONS;
        }
        else if (this.type.isEnum())
        {
            this.suggestions = Arrays.stream(this.type.getEnumConstants()).map(e -> ((Enum<?>) e).name().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableList());
            converter0 = str -> {
                try {
                    @SuppressWarnings({"unchecked", "rawtypes"}) // Raw necessary because of signature. Unchecked because compiler doesn't know T extends Enum
                    T ret = (T)Enum.valueOf((Class<? extends Enum>) type, str.toUpperCase(Locale.ROOT));
                    return ret;
                } catch (IllegalArgumentException e) {
                    throw new InvalidRuleValueException("Valid values for this rule are: " + this.suggestions);
                }
            };
        }
        else
        {
            this.suggestions = List.of();
        }
        if (isStrict && !this.suggestions.isEmpty())
        {
            this.validators.add(0, new Validators.Implementation.Strict<>()); // at 0 prevents validators with side effects from running when invalid
        }
        if (converter0 == null) {
            @SuppressWarnings("unchecked")
            FromStringConverter<T> converterFromMap = (FromStringConverter<T>)CONVERTER_MAP.get(type);
            if (converterFromMap == null) throw new UnsupportedOperationException("Unsupported type for ParsedRule" + type);
            converter0 = converterFromMap;
        }
        this.converter = converter0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // Needed because of the annotation
    private Validator<T> instantiateValidator(Class<? extends Validator> cls)
    {
        try
        {
            Constructor<? extends Validator> constr = cls.getDeclaredConstructor();
            constr.setAccessible(true);
            return constr.newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public void set(CommandSourceStack source, String value) throws InvalidRuleValueException
    {
        set(source, converter.convert(value), value);
    }

    private void set(CommandSourceStack source, T value, String userInput) throws InvalidRuleValueException
    {
        for (Validator<T> validator : this.validators)
        {
            value = validator.validate(source, this, value, userInput); // should this recalculate the string? Another validator may have changed value
            if (value == null) {
                if (source != null) validator.notifyFailure(source, this, userInput);
                throw new InvalidRuleValueException();
            }
        }
        if (!value.equals(value()) || source == null)
        {
            try {
                this.field.set(null, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Couldn't access field for rule: " + name, e);
            }
            if (source != null) settingsManager().notifyRuleChanged(source, this, userInput);
        }
    }

    @Override
    public boolean equals(Object obj)
    {
        return obj instanceof ParsedRule && ((ParsedRule<?>) obj).name.equals(this.name);
    }

    @Override
    public int hashCode()
    {
        return this.name.hashCode();
    }

    @Override
    public String toString()
    {
        return this.name + ": " + RuleHelper.toRuleString(value());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<Component> extraInfo() {
        return getTranslationArray(TranslationKeys.RULE_EXTRA_PREFIX_PATTERN.formatted(settingsManager().identifier(), name()))
                .stream()
                .map(str -> Messenger.c("g " + str))
                .toList();
    }
    
    private List<String> getTranslationArray(String prefix) {
        List<String> ret = new ArrayList<>();
        for (int i = 0; Translations.hasTranslation(prefix + i); i++) {
            ret.add(Translations.tr(prefix + i));
        }
        return ret;
    }

    @Override
    public Collection<String> categories() {
        return categories;
    }

    @Override
    public Collection<String> suggestions() {
        return suggestions;
    }
    
    @Override
    public SettingsManager settingsManager() {
        return settingsManager;
    }

    @Override
    @SuppressWarnings("unchecked") // T comes from the field
    public T value() {
        try {
            return (T) field.get(null);
        } catch (IllegalAccessException e) {
            // Can't happen at regular runtime because we'd have thrown it on construction 
            throw new IllegalArgumentException("Couldn't access field for rule: " + name, e);
        }
    }

    @Override
    public boolean canBeToggledClientSide() {
        return isClient;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    @Override
    public T defaultValue() {
        return defaultValue;
    }

    @Override
    public void set(CommandSourceStack source, T value) throws InvalidRuleValueException {
        set(source, value, RuleHelper.toRuleString(value));
    }

    private static <T> Map.Entry<Class<T>, FromStringConverter<T>> numericalConverter(Class<T> outputClass, Function<String, T> converter) {
        return Map.entry(outputClass, str -> {
            try {
                return converter.apply(str);
            } catch (NumberFormatException e) {
                throw new InvalidRuleValueException("Invalid number for rule");
            }
        });
    }
}
