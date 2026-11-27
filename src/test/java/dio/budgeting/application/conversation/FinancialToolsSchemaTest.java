package dio.budgeting.application.conversation;

import dio.budgeting.domain.Category;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.support.ToolCallbacks;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialToolsSchemaTest {

    @ParameterizedTest
    @ValueSource(strings = {"sum-transactions-by-category", "list-transactions-by-category"})
    void categoryParameterExposesAllEnumValues(String toolName) {
        var tools = new FinancialTools(UUID.randomUUID(), null, null, null);
        var definition = Arrays.stream(ToolCallbacks.from(tools))
                .map(callback -> callback.getToolDefinition())
                .filter(tool -> tool.name().equals(toolName))
                .findFirst().orElseThrow();
        var category = JsonMapper.builder().build().readTree(definition.inputSchema())
                .path("properties").path("category");

        System.out.println(toolName + ": " + category);
        assertThat(category.path("type").asString()).isEqualTo("string");
        assertThat(category.path("enum").isArray()).isTrue();
        assertThat(category.path("enum")).extracting(JsonNode::asString)
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(Category.values()).map(Enum::name).toList());
    }
}
