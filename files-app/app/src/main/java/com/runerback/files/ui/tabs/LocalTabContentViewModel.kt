package com.runerback.files.ui.tabs

import com.runerback.files.data.model.FileSource
import com.runerback.files.data.repository.FileRepositoryFactory

class LocalTabContentViewModel(
    val source: FileSource.Local,
    repositoryFactory: FileRepositoryFactory
) : TabContentViewModel(
    repository = repositoryFactory.create(source),
    initialLoading = source.rootUri.toString().isNotEmpty(),
    ready = source.rootUri.toString().isNotEmpty()
)
