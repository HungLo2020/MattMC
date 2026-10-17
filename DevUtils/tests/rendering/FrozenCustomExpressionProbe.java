import java.util.List;
import net.irisshaders.iris.parsing.IrisFunctions;
import net.irisshaders.iris.parsing.IrisOptions;
import net.stareval.expression.Expression;
import net.stareval.function.FunctionContext;
import net.stareval.function.FunctionReturn;
import net.stareval.function.Type;
import net.stareval.parser.Parser;
import net.stareval.resolver.ExpressionResolver;

/** Read-only evaluator probe. Run with Frozen's compiled classes and dependencies. */
public class FrozenCustomExpressionProbe {
    record Case(String type, String expression) {}

    public static void main(String[] args) throws Exception {
        List<Case> cases = args.length == 0 ? List.of(
            new Case("float", "2147483647+1"),
            new Case("float", "(2147483647+1)*1.0"),
            new Case("float", "2147483647+(1*1.0)"),
            new Case("float", "1/2"),
            new Case("int", "1.5"),
            new Case("int", "int(1.5)"),
            new Case("float", "fmod(-17,16)"),
            new Case("float", "fmod(-17.0,16.0)"),
            new Case("int", "-17%16"),
            new Case("float", "clamp(0.0,2.0,1.0)"),
            new Case("bool", "1==1 || 1==0 && 1==0"),
            new Case("float", "if(1==1,2147483647+1,0.0)"),
            new Case("bool", "true"),
            new Case("bool", "false"),
            new Case("int", "toInt(1.5)"),
            new Case("int", "toInt(1)"),
            new Case("float", "toFloat(1)"),
            new Case("float", "toFloat(1.5)"),
            new Case("int", "1/2"),
            new Case("float", "2147483648"),
            new Case("int", "010"),
            new Case("int", "08"),
            new Case("float", "min(2147483647+1,0.0)"),
            new Case("int", "floor(1.5)+1"),
            new Case("int", "ceil(1.5)+1"),
            new Case("float", "floor(1.5)+2147483647"),
            new Case("float", "floor(1.5)+2147483647.0"),
            new Case("float", "log(2.0,8.0)"),
            new Case("int", "0x20"),
            new Case("int", "0b10"),
            new Case("float", "2.5f"),
            new Case("float", "1e-3")
        ) : java.util.stream.IntStream.range(0, args.length / 2)
            .mapToObj(i -> new Case(args[i * 2], args[i * 2 + 1])).toList();
        if (args.length % 2 != 0) throw new IllegalArgumentException("Use type/expression pairs");
        FunctionContext context = new FunctionContext() {
            public Expression getVariable(String name) { throw new IllegalArgumentException(name); }
            public boolean hasVariable(String name) { return false; }
        };
        for (Case fixture : cases) {
            Type type = switch (fixture.type()) {
                case "float" -> Type.Float;
                case "int" -> Type.Int;
                case "bool" -> Type.Boolean;
                default -> throw new IllegalArgumentException(fixture.type());
            };
            try {
                Expression expression = new ExpressionResolver(IrisFunctions.functions, name -> null)
                    .resolveExpression(type, Parser.parse(fixture.expression(), IrisOptions.options));
                FunctionReturn result = new FunctionReturn();
                expression.evaluateTo(context, result);
                String value = type == Type.Float
                    ? Float.toString(result.floatReturn) + "/0x" + Integer.toHexString(Float.floatToRawIntBits(result.floatReturn))
                    : type == Type.Int ? Integer.toString(result.intReturn) : Boolean.toString(result.booleanReturn);
                System.out.println(fixture.type() + "\t" + fixture.expression() + "\t" + value);
            } catch (RuntimeException failure) {
                System.out.println(fixture.type() + "\t" + fixture.expression() + "\tREJECTED: " + failure.getClass().getSimpleName());
            }
        }
    }
}
