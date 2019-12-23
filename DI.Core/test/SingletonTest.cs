using System.Collections.Generic;
using System.Linq;
using NUnit.Framework;

namespace Runerback.Utils.DI.Test
{
    [TestFixture]
    sealed class SingletonTest
    {
        [SetUp]
        public void SetUp()
        {
            TypeRegister.Register<IFoo, Foo>(IoCScope.SingleInstance);
            TypeResolver.Rebuild();
        }

        [Test]
        public void singleton_instances_should_be_same()
        {
            var impls = Enumerable.Range(0, 10)
                .Select(it => TypeResolver.Self<IFoo>())
                .Distinct()
                .ToArray();
            Assert.AreEqual(1, impls.Length);
        }

        [Test]
        public void singleton_instance_should_have_same_behavior()
        {
            var prop1Set = new HashSet<string>();
            var prop2Set = new HashSet<int>();
            var func1Set = new HashSet<string>();
            var func2Set = new HashSet<string>();

            foreach (var _ in Enumerable.Range(0, 100))
            {
                prop1Set.Add(TypeResolver.Invoke<IFoo, string>(impl => impl.Prop_1));
                prop2Set.Add(TypeResolver.Invoke<IFoo, int>(impl => impl.Prop_2));
                func1Set.Add(TypeResolver.Invoke<IFoo, string>(impl => impl.Method_1()));
                func2Set.Add(TypeResolver.Invoke<IFoo, string>(impl => impl.Method_2()));
            }

            Assert.AreEqual(1, prop1Set.Count);
            Assert.AreEqual(1, prop2Set.Count);
            Assert.AreEqual(1, func1Set.Count);
            Assert.AreEqual(1, func2Set.Count);
        }
    }
}