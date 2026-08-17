package com.runerback.files.ui.tabs

import com.runerback.files.data.model.FileSource
import com.runerback.files.data.repository.FileRepositoryFactory

class SmbTabContentViewModel(
    val source: FileSource.Smb,
    repositoryFactory: FileRepositoryFactory
) : TabContentViewModel(
    repository = repositoryFactory.create(source),
    initialLoading = true
)
