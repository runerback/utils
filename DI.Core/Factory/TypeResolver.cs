using Microsoft.Extensions.DependencyInjection;
using System;
using System.Collections.Generic;
using System.Threading.Tasks;

namespace Runerback.Utils.DI
{
    public static class TypeResolver
    {
        static TypeResolver()
        {
            Rebuild();
        }

        private static IServiceProvider serviceProvider;

        public static void Rebuild()
        {
            serviceProvider = BuildServiceProvider(TypeRegister.RegistedTypes);
        }

        private static IServiceProvider BuildServiceProvider(IEnumerable<IocData> types)
        {
            var services = new ServiceCollection();

            foreach (var type in types)
            {
                switch (type.Scope)
                {
                    case IoCScope.InstancePerLifetimeScope:
                        services.AddScoped(type.ServiceType, type.ImplType);
                        break;
                    case IoCScope.SingleInstance:
                        services.AddSingleton(type.ServiceType, type.ImplType);
                        break;
                    default: continue;
                }
            }

            return services.BuildServiceProvider();
        }

        /// <summary>
        /// Resolve TDef service under lifetime scope
        /// </summary>
        public static void Resolve<TDef>(Action<TDef> callback)
        {
            if (callback == null)
                return;

            //always use local scope
            using var scope = serviceProvider.CreateScope();
            var impl = scope.ServiceProvider.GetService<TDef>();
            callback(impl);
        }

        public static async Task ResolveAsync<TDef>(Action<TDef> callback)
        {
            if (callback == null)
                return;

            //always use local scope
            using var scope = serviceProvider.CreateScope();
            var impl = scope.ServiceProvider.GetService<TDef>();
            await Task.Run(() => callback(impl));
        }

        public static async Task ResolveAsync<TDef>(Func<TDef, Task> callback)
        {
            if (callback == null)
                return;

            //always use local scope
            using var scope = serviceProvider.CreateScope();
            var impl = scope.ServiceProvider.GetService<TDef>();
            await callback(impl);
        }

        public static TResult Invoke<TDef, TResult>(Func<TDef, TResult> callback)
        {
            if (callback == null)
                return default(TResult);

            //always use local scope
            using var scope = serviceProvider.CreateScope();
            var impl = scope.ServiceProvider.GetService<TDef>();
            return callback(impl);
        }

        public static async Task<TResult> InvokeAsync<TDef, TResult>(Func<TDef, TResult> callback)
        {
            if (callback == null)
                return default(TResult);

            //always use local scope
            using var scope = serviceProvider.CreateScope();
            var impl = scope.ServiceProvider.GetService<TDef>();
            return await Task.Run(() => callback(impl));
        }

        public static async Task<TResult> InvokeAsync<TDef, TResult>(Func<TDef, Task<TResult>> callback)
        {
            if (callback == null)
                return default(TResult);

            //always use local scope
            using var scope = serviceProvider.CreateScope();
            var impl = scope.ServiceProvider.GetService<TDef>();
            return await callback(impl);
        }

        /// <summary>
        /// be careful since TDef was out of scope
        /// </summary>
        public static TDef Self<TDef>()
        {
            //always use local scope
            using var scope = serviceProvider.CreateScope();
            return scope.ServiceProvider.GetService<TDef>();
        }
    }
}