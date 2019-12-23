using System.Collections.Generic;
using System.Linq;
using NUnit.Framework;

namespace Runerback.Utils.DI.Test
{
    sealed class ScopedTest
    {
        [SetUp]
        public void SetUp()
        {
            TypeRegister.Register<IFoo, Foo>(IoCScope.InstancePerLifetimeScope);
            TypeResolver.Rebuild();
        }

        [Test]
        public void scoped_instance_should_be_diff_under_diff_scope()
        {
            int count = 10;
            var impls = Enumerable.Range(0, count)
                .Select(it => TypeResolver.Self<IFoo>())
                .Distinct()
                .ToArray();
            Assert.AreEqual(count, impls.Length);
        }

        [Test]
        public void scoped_instance_should_have_diff_behavior_under_diff_scope()
        {
            var count = 100;

            var prop1Set = new HashSet<string>();
            var prop2Set = new HashSet<int>();
            var func1Set = new HashSet<string>();
            var func2Set = new HashSet<string>();

            foreach (var _ in Enumerable.Range(0, count))
            {
                prop1Set.Add(TypeResolver.Invoke<IFoo, string>(impl => impl.Prop_1));
                prop2Set.Add(TypeResolver.Invoke<IFoo, int>(impl => impl.Prop_2));
                func1Set.Add(TypeResolver.Invoke<IFoo, string>(impl => impl.Method_1()));
                func2Set.Add(TypeResolver.Invoke<IFoo, string>(impl => impl.Method_2()));
            }

            Assert.AreEqual(count, prop1Set.Count);
            Assert.AreEqual(count, prop2Set.Count);
            Assert.AreEqual(count, func1Set.Count);
            Assert.AreEqual(count, func2Set.Count);
        }
    }
}