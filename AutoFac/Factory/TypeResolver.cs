using Autofac;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace Runerback.Utils.AutoFac
{
    public static class TypeResolver
    {
        static TypeResolver()
        {
            Rebuild();
        }

        private static IContainer container;

        public static void Rebuild()
        {
            if (container != null)
                container.Dispose();
            container = BuildContainer(TypeRegister.RegistedTypes);
        }

        private static IContainer BuildContainer(IEnumerable<IocData> types)
        {
            var builder = new ContainerBuilder();

            foreach (var type in types)
            {
                var regBuilder = builder.RegisterType(type.ImplType).As(type.ServiceType);
                switch (type.Scope)
                {
                    case IoCScope.InstancePerLifetimeScope:
                        regBuilder.InstancePerLifetimeScope();
                        break;
                    case IoCScope.SingleInstance:
                        regBuilder.SingleInstance();
                        break;
                    default: continue;
                }
            }

            return builder.Build();
        }

        /// <summary>
        /// Resolve TDef service under lifetime scope
        /// </summary>
        public static void Resolve<TDef>(Action<TDef> callback)
        {
            if (callback == null)
                return;

            using (var scope = container.BeginLifetimeScope())
            {
                if (!scope.TryResolve(out TDef impl))
                    throw new KeyNotFoundException($"{typeof(TDef).Name} was not registered");
                callback(impl);
            }
        }

        public static async Task ResolveAsync<TDef>(Action<TDef> callback)
        {
            if (callback == null)
                return;

            using (var scope = container.BeginLifetimeScope())
            {
                if (!scope.TryResolve(out TDef impl))
                    throw new KeyNotFoundException($"{typeof(TDef).Name} was not registered");
                await Task.Run(() => callback(impl));
            }
        }

        public static async Task ResolveAsync<TDef>(Func<TDef, Task> callback)
        {
            if (callback == null)
                return;

            using (var scope = container.BeginLifetimeScope())
            {
                if (!scope.TryResolve(out TDef impl))
                    throw new KeyNotFoundException($"{typeof(TDef).Name} was not registered");
                await callback(impl);
            }
        }

        public static TResult Invoke<TDef, TResult>(Func<TDef, TResult> callback)
        {
            if (callback == null)
                return default(TResult);

            using (var scope = container.BeginLifetimeScope())
            {
                if (!scope.TryResolve(out TDef impl))
                    throw new KeyNotFoundException($"{typeof(TDef).Name} was not registered");
                return callback(impl);
            }
        }

        public static async Task<TResult> InvokeAsync<TDef, TResult>(Func<TDef, TResult> callback)
        {
            if (callback == null)
                return default(TResult);

            using (var scope = container.BeginLifetimeScope())
            {
                if (!scope.TryResolve(out TDef impl))
                    throw new KeyNotFoundException($"{typeof(TDef).Name} was not registered");
                return await Task.Run(() => callback(impl));
            }
        }

        public static async Task<TResult> InvokeAsync<TDef, TResult>(Func<TDef, Task<TResult>> callback)
        {
            if (callback == null)
                return default(TResult);

            using (var scope = container.BeginLifetimeScope())
            {
                if (!scope.TryResolve(out TDef impl))
                    throw new KeyNotFoundException($"{typeof(TDef).Name} was not registered");
                return await callback(impl);
            }
        }
    }
}
