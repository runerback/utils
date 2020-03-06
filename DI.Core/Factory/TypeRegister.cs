using System;
using System.Collections.Concurrent;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Reflection;

namespace Runerback.Utils.DI
{
    public static class TypeRegister
    {
        private static IocList registerdTypes = new IocList();
        internal static IEnumerable<IocData> RegistedTypes => registerdTypes.ToArray();

        public static void Register<TDef, TImpl>(IoCScope scope = IoCScope.InstancePerLifetimeScope)
            where TImpl : class, TDef, new()
        {
            registerdTypes.Add(IocData.Create<TDef, TImpl>(scope));
        }

        public static void RegisterAll(string projDllPrefix)
        {
            var currentDir = Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
            var projDlls = Enumerable.Empty<string>()
                .Concat(Directory.GetFiles(currentDir, "*.dll"))
                .Concat(Directory.GetFiles(currentDir, "*exe"))
                .Select(it =>
                {
                    try { return Assembly.LoadFrom(it); }
                    catch { return null; }
                })
                .Where(it => it != null);

            if (!string.IsNullOrWhiteSpace(projDllPrefix))
                projDlls = projDlls.Where(it => it.GetName().Name.StartsWith(projDllPrefix));

            var factoryTypes = typeof(IFactoryTypes);
            var registers = projDlls
                .SelectMany(it => it.GetTypes())
                .Where(it => it.IsClass && !it.IsAbstract && factoryTypes.IsAssignableFrom(it))
                .Where(it => it.GetConstructor(Type.EmptyTypes) != null)
                .Select(it => Activator.CreateInstance(it))
                .Cast<IFactoryTypes>();

            foreach (var typeRegister in registers)
                typeRegister.Register();
        }

        public static void Reset()
        {
            registerdTypes.Clear();
        }

        sealed class IocList
        {
            private readonly ConcurrentDictionary<Type, IocData> store =
                new ConcurrentDictionary<Type, IocData>();

            public void Add(IocData data)
            {
                if (data == null)
                    return;

                Validate(data);
                store.AddOrUpdate(data.ServiceType, data, (k, v) => data);
            }

            private void Validate(IocData data)
            {
                if (data == null)
                    throw new ArgumentNullException(nameof(data));

                if (!data.ServiceType.IsInterface)
                    throw new ArgumentException("service type should be interface");

                if (!data.ImplType.IsClass || data.ImplType.IsAbstract)
                    throw new ArgumentException("implement type should be non abstract class");
            }

            public void Clear()
            {
                store.Clear();
            }

            public IocData[] ToArray() => store.Values.ToArray();
        }
    }
}
