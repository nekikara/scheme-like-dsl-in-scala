package example.core

sealed abstract class Expression {
  def ++(es: Expression*): Exp = {
    if (es.isEmpty) {
      Exp(this, Num(0))
    } else {
      es.tail.foldLeft(Exp(this, es.head)) {(acc, exp) => {
        Exp(acc.es :+ exp:_*)
      }}
    }
  }
}
case class Exp(es: Expression*) extends Expression
case class Sym(s: Symbol) extends Expression
case class Num(n: Int) extends Expression {
  def +(that: Num) = Num(this.n + that.n)
  def -(that: Num) = Num(this.n - that.n)
  def *(that: Num) = Num(this.n * that.n)
}
case class Plus() extends Expression
case class Minus() extends Expression
case class Multiply() extends Expression
case class Let(binds: Binds, body: Expression) extends Expression
case class Bind(s: Sym, n: Expression) extends Expression
case class Binds(binds: Bind*) extends Expression
case class Lambda(vars: Vars, body: Expression) extends Expression
case class Vars(vars: Sym*) extends Expression
case class Closure(params: Vars, body: Expression, envStack: EnvStack) extends Expression

// Env
case class Env(map: Map[Sym, Expression])
case class EnvStack(envs: List[Env])

object Weather {
  def eval(exp: Expression, envStack: EnvStack = EnvStack(List.empty[Env])): Num =  exp match {
    case Num(n) => Num(n)
    case Let(binds, body) => evalLet(binds, body, envStack)
    case Sym(x) => eval(lookupVars(Sym(x), envStack), envStack)
    case Exp(ope, es@_*) => evalOperation(ope, envStack, es:_*)
    case _ => throw new RuntimeException("Can't match AST")
  }

  def operate(fun: (Num, Num) => Num, envStack: EnvStack, exps: Expression*): Num = {
    val nums = exps.map {
      case Num(n) => Num(n)
      case x => eval(x, envStack)
    }
    nums.tail.foldLeft(nums.head)(fun)
  }

  def evalOperation(ope: Expression, stack: EnvStack, es: Expression*): Num = {
    ope match {
      case Lambda(vars, body) => evalClosure(vars, body, stack, es:_*)
      case Closure(vars, body, localEnv) => evalClosure(vars, body, localEnv, es:_*)
      case Plus() => operate(plus, stack, es:_*)
      case Minus() => operate(minus, stack, es:_*)
      case Multiply() => operate(multiply, stack, es:_*)
      case Sym(x) => {
        val exp = lookupVars(Sym(x), stack)
        val newExp = exp ++ (es:_*)
        eval(newExp, stack)
      }
      case _ => throw new RuntimeException("Can't match any Operation")
    }
  }

  def plus(x: Num, y: Num): Num = x + y
  def minus(x: Num, y: Num): Num = x - y
  def multiply(x: Num, y: Num): Num = x * y

  def convertLetToLambda(binds: Binds, body: Expression): (Lambda, List[Expression]) = {
    val (vars, args) = divideParamsAndArgs(binds)
    val lambda = Lambda(Vars(vars:_*), body)
    (lambda, args)
  }

  def evalLet(binds: Binds, body: Expression, envStack: EnvStack): Num = {
    val (lambda, args) = convertLetToLambda(binds, body)
    val closure = convertLambdaToClosure(lambda.vars, lambda.body, envStack)
    val newExp = args.tail.foldLeft(Exp(closure, args.head)) { (acc, arg) => {
      Exp(acc.es :+ arg:_*)
    }}
    eval(newExp, envStack)
  }

  def divideParamsAndArgs(binds: Binds): (List[Sym], List[Expression]) = {
    val bs = binds.binds
    bs.tail.foldLeft((List(bs.head.s), List(bs.head.n))) { (acc, b) => (acc._1 :+ b.s, acc._2 :+ b.n) }
  }

  def convertLambdaToClosure(vars: Vars, body: Expression, stack: EnvStack): Closure = {
    Closure(vars, body, stack)
  }

  def evalClosure(vars: Vars, body: Expression, localEnv: EnvStack, args: Expression*): Num = {
    val newEnvStack = extendEnvStack(localEnv, vars, args)
    eval(body, newEnvStack)
  }

  def extendEnvStack(stack: EnvStack, vars: Vars, exps: Seq[Expression]): EnvStack = {
    val list = vars.vars.zip(exps)
    val envMap = list.foldLeft(Map[Sym, Expression]().empty) { (acc, vr) =>
      val e = vr._2 match {
        case Lambda(vars, body) => convertLambdaToClosure(vars, body, stack)
        case _ => vr._2
      }
      acc + (vr._1 -> e)
    }
    EnvStack(Env(envMap) :: stack.envs)
  }
  def lookupVars(symbol: Sym, stack: EnvStack): Expression = {
    val env = stack.envs.find(env => {
      env.map.find(pair =>  pair._1 == symbol) match {
        case Some(_) => true
        case None => false
      }
    })
    env match {
      case Some(x) => x.map(symbol) match {
        case Num(n) => Num(n)
        case x => x
      }
      case None => throw new RuntimeException("Not found the var")
    }
  }
}
