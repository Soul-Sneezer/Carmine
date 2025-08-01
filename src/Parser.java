import java.util.ArrayList;
import java.util.List;

public class Parser
{
    private List<Token> tokens = new ArrayList<Token>();
    private int current = 0;
    private boolean hadError = false;

    private boolean isAtEnd()
    {
        return peek().type == TokenType.EOF;
    }

    Parser(List<Token> tokens)
    {
        this.tokens = tokens;
    }

    private Token peek()
    {
        return tokens.get(current);
    }

    private Token advance()
    {
        if (isAtEnd())
            return null;

        return tokens.get(current++);
    }

    private Token previous()
    {
        return tokens.get(current - 1);
    }

    private void errorAtCurrent(String message)
    {
         Logger.log(peek(), message, LogLevel.ERROR) ;
    }

    private boolean check(TokenType type)
    {
        if (isAtEnd())
            return false;

        return peek().type == type;
    }

    private boolean match(TokenType... types)
    {
        for (TokenType type : types) {
            if (peek().type == type) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Expr expression()
    {
        Expr left = or();

        while (match(TokenType.ASSIGN))
        {
            Expr right = expression();

            if (left instanceof Expr.Variable)
            {
                return new Expr.Assignment(((Expr.Variable)left).getName(), right);
            }

            errorAtCurrent("Invalid assignment target.");
        }

        return left;
    }

    private Expr or()
    {
        Expr expr = and();

        while (match(TokenType.OR))
        {
            Token op = previous();
            Expr right = and();

            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    private Expr and()
    {
        Expr expr = equality();

        while (match(TokenType.AND))
        {
            Token op = previous();
            Expr right = equality();

            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    private Expr equality()
    {
        Expr expr = comparison();

        while (match(TokenType.EQUAL) || match(TokenType.NOTEQUAL))
        {
            Token op = previous();
            Expr right = comparison();

            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    private Expr comparison()
    {
        Expr expr = term();

        while (match(TokenType.GREATER) || match(TokenType.LESS) || match(TokenType.GREATER_EQUAL) || match(TokenType.LESS_EQUAL))
        {
            Token op = previous();
            Expr right = term();

            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    private Expr term()
    {
        Expr expr = factor();

        while (match(TokenType.MINUS) || match(TokenType.PLUS))
        {
            Token op = previous();
            Expr right = factor();

            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    private Expr factor()
    {
        Expr expr = unary();

        while (match(TokenType.DIV) || match(TokenType.MOD) || match(TokenType.MUL))
        {
            Token op = previous();
            Expr right = unary();

            expr = new Expr.Binary(expr, op, right);
        }

        return expr;
    }

    private Expr unary()
    {
        if (match(TokenType.NOT) || match(TokenType.MINUS))
        {
            Token op = previous();
            Expr right = call();

            return new Expr.Unary(op, right);
        }

        return call();
    }

    private List<Expr> arguments()
    {
        List<Expr> args = new ArrayList<>();

        if (!match(TokenType.RPAREN))
        {
            do
            {
                args.add(expression());
            } while (match(TokenType.COMMA));

            if (!match(TokenType.RPAREN))
                errorAtCurrent("Expected ')'.");
        }

        return args;
    }

    private Expr call()
    {
        Expr expr = primary();

        if (match(TokenType.LPAREN))
        {
            List<Expr> args = arguments();

            return new Expr.Call(peek().line, expr, args);
        }
        else
        {
            return expr;
        }
    }

    private Expr primary()
    {
        if (match(TokenType.TRUE))
            return new Expr.Literal(peek().line,true);

        if (match(TokenType.FALSE))
            return new Expr.Literal(peek().line, false);

        if (match(TokenType.LPAREN))
        {
            Expr expr = expression();
            if (!match(TokenType.RPAREN))
                errorAtCurrent("Expected matching ')' for '('.");

            return new Expr.Group(peek().line, expr);
        }

        if (match(TokenType.DECIMAL))
            return new Expr.Literal(peek().line, Integer.parseInt(previous().lexeme));

        if (match(TokenType.HEXADECIMAL))
            return new Expr.Literal(peek().line, Integer.parseInt(previous().lexeme, 16));

        if (match(TokenType.BINARY))
            return new Expr.Literal(peek().line, Integer.parseInt(previous().lexeme, 2));

        if (match(TokenType.NULL))
            return new Expr.Literal(peek().line, null);

        if (match(TokenType.IDENTIFIER))
            return new Expr.Variable(previous());

        errorAtCurrent("Unexpected token: " + peek());
        hadError = true;
        return null;
    }

    private Stmt expressionStmt()
    {
        Expr expr = expression();
        /*
        if (!match(TokenType.ENDLINE) && !match(TokenType.EOF))
        {
            hadError = true;
            Carmine.error(peek().line + " Invalid expression.");
        }
         */

        if (!match(TokenType.SEMICOLON))
        {
            errorAtCurrent("Expected ';' at end of statement.");
            return null;
        }

        return new Stmt.Expression(expr);
    }

    private Stmt blockStatement()
    {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd() && !match(TokenType.RBRACE))
        {
            statements.add(declaration());
        }
        //match(TokenType.ENDLINE);
        return new Stmt.Block(statements);
    }

    private Stmt moduleStatement() // could either be a variable or a function
    {
        if(!match(TokenType.IDENTIFIER))
        {
            errorAtCurrent("Unexpected token in moduleStatement: " + peek());

            return null;
        }

        Token name = previous();

        if (match(TokenType.LPAREN)) // then it's a function declaration and that function returns a module
        {
            List<Token> params = new ArrayList<>();
            List<Token> returnValues = new ArrayList<>();
            if (!check(TokenType.RPAREN))
            {
                do
                {
                    params.add(advance());
                } while(match(TokenType.COMMA));
            }

            if (!match(TokenType.RPAREN))
                errorAtCurrent("Expected ')'.");

            if (match(TokenType.ARROW)) // then it returns one or multiple values
            {
                do
                {
                    returnValues.add(advance());
                } while(match(TokenType.COMMA));
            }

            if (!match(TokenType.LBRACE))
                errorAtCurrent("Expected '{'.");

            Stmt statements = blockStatement();

            if (statements instanceof Stmt.Block)
                return new Stmt.ModuleFunction(name, params, returnValues, (Stmt.Block)statements);
            else
                errorAtCurrent("Expected block statement.");

            return null;
        }

        if (match(TokenType.ASSIGN))
        {
            Expr right = expression();
            // match(TokenType.ENDLINE);
            if (!match(TokenType.SEMICOLON)) {
                errorAtCurrent("Expected ';' at end of statement.");
                return null;
            }

            return new Stmt.Module(name, right);
        }

        //match(TokenType.ENDLINE);
        if (!match(TokenType.SEMICOLON)) {
            errorAtCurrent("Expected ';' at end of statement.");
            return null;
        }

        return new Stmt.Module(name, null);
    }

    private Stmt constStatement() // could either be a variable or a function
    {
        if(!match(TokenType.IDENTIFIER))
        {
            errorAtCurrent("Unexpected token: " + peek());

            return null;
        }

        Token name = previous();

        if (match(TokenType.LPAREN)) // then it's a function declaration and that function returns a const
        {
            List<Token> params = new ArrayList<>();
            List<Token> returnValues = new ArrayList<>();
            if (!check(TokenType.RPAREN))
            {
                do
                {
                    params.add(advance());
                } while(match(TokenType.COMMA));
            }

            if (!match(TokenType.RPAREN))
                errorAtCurrent("Expected ')'.");

            if (match(TokenType.ARROW)) // then it returns one or multiple values
            {
                do
                {
                    returnValues.add(advance());
                } while(match(TokenType.COMMA));
            }

            if (!match(TokenType.LBRACE))
                errorAtCurrent("Expected '{'.");

            Stmt statements = blockStatement();

            if (statements instanceof Stmt.Block)
                return new Stmt.ConstFunction(name, params, returnValues, (Stmt.Block)statements);
            else
                errorAtCurrent("Expected block statement.");

            return null;
        }

        if (match(TokenType.ASSIGN))
        {
            Expr right = expression();
           // match(TokenType.ENDLINE);
            if (!match(TokenType.SEMICOLON)) {
                errorAtCurrent("Expected ';' at end of statement.");
                return null;
            }

            return new Stmt.Const(name, right);
        }

        //match(TokenType.ENDLINE);
        if (!match(TokenType.SEMICOLON)) {
            errorAtCurrent("Expected ';' at end of statement.");
            return null;
        }

        return new Stmt.Const(name, null);
    }

    private Stmt enumStatement()
    {
        boolean found_brace = false;
        Token name = null;

        if (match(TokenType.IDENTIFIER))
            name = previous();

       // match(TokenType.ENDLINE);
        if (!match(TokenType.LBRACE))
             errorAtCurrent("Expected '{'.");

        ArrayList<Expr.Assignment> assignments = new ArrayList<>();

        do
        {
           // match(TokenType.ENDLINE);
            if (match(TokenType.RBRACE)) {
                found_brace = true;
                break;
            }
            Expr assignment = expression();
            if (!(assignment instanceof Expr.Assignment))
            {
                 errorAtCurrent("Invalid assignment.");
            }
            assignments.add((Expr.Assignment)assignment);
        } while (match(TokenType.COMMA));

        if (!match(TokenType.RBRACE) && !found_brace)
             errorAtCurrent("Expected '}'.");

        if (!match(TokenType.SEMICOLON)) {
            errorAtCurrent("Expected ';' at end of statement.");
            return null;
        }

        return new Stmt.Enum(name, assignments);
    }

    private Stmt ifStatement()
    {
        Expr condition = expression();

        Stmt thenBranch = statement();
        if (match(TokenType.ELSE))
        {
            Stmt elseBranch;
            if (match(TokenType.IF))
            {
                elseBranch = ifStatement();
            }
            else
            {
                elseBranch = statement();
            }

            return new Stmt.If(condition, thenBranch, elseBranch);
        }

        return new Stmt.If(condition, thenBranch, null);
    }

    private Stmt whileStatement()
    {
        Expr condition = expression();

        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    private Stmt forStatement() // unused right now, still unsure about for syntax
    {
        Expr init = expression();

        if (!(init instanceof Expr.Variable))
        {
             errorAtCurrent("Invalid variable.");
        }

        if (!(peek().lexeme.contentEquals("in")))
             errorAtCurrent("Missing 'in' keyword.");

        advance();

        Expr minValue = expression();

        if (!match(TokenType.DOT))
        {

        }
        if (!match(TokenType.DOT))
        {
             errorAtCurrent("Missing '..' keyword.");
        }

        Expr maxValue = expression();

        Stmt body = statement();
        return new Stmt.For(init, minValue, maxValue, body);
    }

    private Stmt declaration()
    {
       // while (match(TokenType.ENDLINE)) ;

        if (match(TokenType.MODULE))
            return moduleStatement();
        else if (match(TokenType.CONST))
            return constStatement();
        else if (match(TokenType.ENUM))
            return enumStatement();
        else
            return statement();
    }

    private Stmt statement() {
       // while (match(TokenType.ENDLINE));

        if (match(TokenType.LBRACE))
            return blockStatement();
        else if (match(TokenType.IF))
            return ifStatement();
        else if (match(TokenType.WHILE))
            return whileStatement();
        else if (match(TokenType.FOR))
            return forStatement();

        return expressionStmt();
    }

    public List<Stmt> parse()
    {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd())
        {
            statements.add(declaration());
        }

        return statements;
    }
}
