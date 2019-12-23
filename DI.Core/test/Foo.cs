using System;

namespace Runerback.Utils.DI.Test
{
    sealed class Foo : IFoo
    {
        private static int prop2ValueCounter = int.MinValue;
        private int prop2Value = checked(prop2ValueCounter++);
        private readonly string method1Value = Guid.NewGuid().ToString();
        private readonly string method2Value = Guid.NewGuid().ToString();

        public string Prop_1 { get; } = Guid.NewGuid().ToString();

        public int Prop_2 => prop2Value;

        public string Method_1() => method1Value;

        public string Method_2() => method2Value;
    }
}